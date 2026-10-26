"""Internal API; run on loopback or a private container network only."""
from contextlib import asynccontextmanager
import os
from pathlib import Path
from fastapi import FastAPI, HTTPException, Query
from .model import ContentModel


def create_app(data_dir: Path | None = None) -> FastAPI:
    directory = data_dir or Path(os.getenv("BV_MODEL_DATA_DIR", Path(__file__).parent / "data"))

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.content_model = ContentModel(directory)
        yield

    app = FastAPI(title="Bước Việt Content Model", version="1.0.0", lifespan=lifespan)

    @app.get("/health")
    def health():
        return {"status": "ok", "modelLoaded": True, "canonicalPlaces": len(app.state.content_model.by_code)}

    @app.get("/model")
    def model():
        return app.state.content_model.metadata

    @app.get("/similar/{canonicalCode}")
    def similar(canonicalCode: str, limit: int = Query(default=5, ge=1, le=20)):
        try:
            return app.state.content_model.similar(canonicalCode, limit)
        except KeyError:
            raise HTTPException(status_code=404, detail="Không tìm thấy mã địa danh") from None

    return app


app = create_app()
