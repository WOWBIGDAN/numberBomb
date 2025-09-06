#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数字炸弹游戏 - Python 版后台（asyncio TCP）
兼容协议：
- JOIN:<player>
- GUESS:<player>:<num>
- START:
- STATUS:
"""

import asyncio
import random
from typing import Dict, List, Optional, Tuple


class Player:
	def __init__(self, name: str, writer: asyncio.StreamWriter):
		self.name = name
		self.writer = writer
		self.order_index: int = -1
		self.current_guess: Optional[int] = None

	@property
	def connected(self) -> bool:
		return not self.writer.is_closing()

	async def send(self, message: str) -> None:
		if self.connected:
			self.writer.write((message + "\n").encode("utf-8"))
			await self.writer.drain()


class GameSession:
	def __init__(self) -> None:
		self.players: List[Player] = []
		self.state: str = "WAITING_FOR_PLAYERS"
		self.bomb: Optional[int] = None
		self.min_range: int = 0
		self.max_range: int = 200
		self.current_index: int = 0

	def get_player_by_name(self, name: str) -> Optional[Player]:
		for p in self.players:
			if p.name == name:
				return p
		return None

	async def add_player(self, player: Player) -> bool:
		if self.state != "WAITING_FOR_PLAYERS":
			await player.send("当前不可加入，游戏已开始或已结束")
			return False
		if self.get_player_by_name(player.name):
			await player.send("玩家名已存在，请更换名称")
			return False
		self.players.append(player)
		await player.send("欢迎加入数字炸弹游戏！等待其他玩家...")
		await self.broadcast(f"玩家 {player.name} 加入游戏，当前玩家数: {len(self.players)}")
		return True

	async def remove_player(self, name: str) -> None:
		p = self.get_player_by_name(name)
		if p:
			self.players.remove(p)
			await self.broadcast(f"玩家 {name} 离开游戏，当前玩家数: {len(self.players)}")
			if self.state == "PLAYING":
				await self.end_game()

	async def start_game(self) -> None:
		if len(self.players) < 2:
			await self.broadcast("需要至少2名玩家才能开始游戏！")
			return
		self.state = "READY_TO_START"
		self.bomb = random.randint(0, 200)
		random.shuffle(self.players)
		for idx, p in enumerate(self.players):
			p.order_index = idx
		self.current_index = 0
		self.min_range, self.max_range = 0, 200
		await self.broadcast("=== 游戏开始！ ===")
		await self.broadcast("炸弹数字已设置，范围: 0-200")
		order_msg = "玩家顺序:\n" + "\n".join(f"{i+1}. {p.name}" for i, p in enumerate(self.players))
		await self.broadcast(order_msg)
		self.state = "PLAYING"
		await self.notify_turn()

	async def end_game(self) -> None:
		self.state = "FINISHED"
		await self.broadcast("=== 游戏结束 ===")

	async def process_guess(self, player_name: str, guess: int) -> None:
		if self.state != "PLAYING":
			return
		if self.current_index >= len(self.players):
			return
		current_player = self.players[self.current_index]
		if current_player.name != player_name:
			return
		if guess < self.min_range or guess > self.max_range:
			await current_player.send(f"数字必须在 {self.min_range} 到 {self.max_range} 之间！")
			return

		current_player.current_guess = guess

		if guess == self.bomb:
			await self.broadcast(f"{player_name} 猜测: {guess} - 炸弹爆炸！游戏结束！")
			await self.broadcast(f"获胜者: {player_name}！")
			await self.end_game()
			return

		is_greater = guess > self.bomb  # type: ignore[arg-type]
		if is_greater:
			self.max_range = guess - 1
			result_msg = f"{player_name} 猜测: {guess} - 炸弹数字更小"
		else:
			self.min_range = guess + 1
			result_msg = f"{player_name} 猜测: {guess} - 炸弹数字更大"

		result_msg += f"\n新范围: {self.min_range}-{self.max_range}"
		await self.broadcast(result_msg)

		self.current_index = (self.current_index + 1) % len(self.players)
		await self.notify_turn()

	async def notify_turn(self) -> None:
		if self.state != "PLAYING" or not self.players:
			return
		p = self.players[self.current_index]
		await p.send("你的回合！请输入一个数字: ")
		await self.broadcast(
			f"轮到 {p.name} 猜数字 (范围: {self.min_range}-{self.max_range})"
		)

	async def broadcast(self, message: str) -> None:
		for p in list(self.players):
			if p.connected:
				await p.send(message)

	def game_state_message(self) -> str:
		msg = ["=== 游戏状态 ==="]
		msg.append(f"状态: {self.state}")
		msg.append(f"玩家数量: {len(self.players)}")
		if self.state == "PLAYING":
			msg.append(f"当前范围: {self.min_range}-{self.max_range}")
			if 0 <= self.current_index < len(self.players):
				msg.append(f"当前玩家: {self.players[self.current_index].name}")
		return "\n".join(msg)


class BombServer:
	def __init__(self, host: str = "0.0.0.0", port: int = 8888) -> None:
		self.host = host
		self.port = port
		self.session = GameSession()

	async def handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
		peer = writer.get_extra_info("peername")
		print(f"新客户端连接: {peer}")
		player: Optional[Player] = None
		try:
			while True:
				line = await reader.readline()
				if not line:
					break
				message = line.decode("utf-8").strip()
				if not message:
					continue
				cmd, data = self.parse_message(message)
				if cmd == "JOIN":
					name = data.strip() or "玩家"
					player = Player(name, writer)
					ok = await self.session.add_player(player)
					if not ok:
						break
				elif cmd == "GUESS":
					parts = data.split(":", 1)
					if len(parts) == 2:
						name, guess_str = parts
						try:
							guess = int(guess_str)
							await self.session.process_guess(name, guess)
						except ValueError:
							pass
				elif cmd == "START":
					await self.session.start_game()
				elif cmd == "STATUS":
					await self.send_line(writer, self.session.game_state_message())
				else:
					print(f"未知命令: {cmd}")
		except asyncio.CancelledError:
			raise
		except Exception as e:
			print(f"客户端错误: {e}")
		finally:
			if player is not None:
				await self.session.remove_player(player.name)
			try:
				writer.close()
				await writer.wait_closed()
			except Exception:
				pass
			print(f"客户端断开: {peer}")

	@staticmethod
	def parse_message(message: str) -> Tuple[str, str]:
		if ":" in message:
			pos = message.find(":")
			return message[:pos], message[pos + 1 :]
		return message, ""

	@staticmethod
	async def send_line(writer: asyncio.StreamWriter, text: str) -> None:
		writer.write((text + "\n").encode("utf-8"))
		await writer.drain()

	async def run(self) -> None:
		server = await asyncio.start_server(self.handle_client, self.host, self.port)
		addr = ", ".join(str(sock.getsockname()) for sock in server.sockets or [])
		print(f"服务器启动成功，监听: {addr}")
		async with server:
			await server.serve_forever()


def main() -> None:
	import argparse
	parser = argparse.ArgumentParser(description="数字炸弹 Python 服务器")
	parser.add_argument("--host", default="0.0.0.0")
	parser.add_argument("--port", type=int, default=8889)
	args = parser.parse_args()

	server = BombServer(args.host, args.port)
	try:
		asyncio.run(server.run())
	except KeyboardInterrupt:
		print("\n收到中断信号，正在退出...")


if __name__ == "__main__":
	main()
