import asyncio
import os
import requests
from nats.aio.client import Client as NATS


async def main():
    nc = NATS()
    nats_url = os.getenv("NATS_URL", "nats://my-nats.nats.svc.cluster.local:4222")
    webhook_url = os.getenv("WEBHOOK_URL")

    print(f"Connecting to NATS at {nats_url}...")
    await nc.connect(nats_url, connect_timeout=10, max_reconnect_attempts=5)
    print("Connected successfully!")

    async def message_handler(msg):
        data = msg.data.decode()
        print(f"Received message: {data}")
        if webhook_url:
            payload = {"user": "bot", "message": data}
            try:
                response = requests.post(webhook_url, json=payload, timeout=5)
                print(f"Webhook response status: {response.status_code}")
            except requests.RequestException as e:
                print(f"Webhook failed: {e}")

    await nc.subscribe("todo_updates", queue="broadcaster_group", cb=message_handler)

    print("Broadcaster is listening for updates...")
    while True:
        await asyncio.sleep(1)


if __name__ == '__main__':
    asyncio.run(main())