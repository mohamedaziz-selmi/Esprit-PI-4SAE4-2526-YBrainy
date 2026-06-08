import os

import uvicorn


if __name__ == "__main__":
    port = int(os.getenv("SERVICE_PORT", "9010"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
