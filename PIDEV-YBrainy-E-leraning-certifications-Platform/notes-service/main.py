import os
from fastapi import FastAPI, Depends, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from typing import List, Optional
import py_eureka_client.eureka_client as eureka_client
import models, schemas
from database import engine, get_db

models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="YBrainy Notes Service", version="1.0.0")

_allowed_origins = os.getenv("ALLOWED_ORIGINS", "http://localhost:4200,http://localhost:4201").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_methods=["*"],
    allow_headers=["*"],
    allow_credentials=True,
)

@app.on_event("startup")
async def startup():
    eureka_server = os.getenv("EUREKA_SERVER", "http://localhost:8761/eureka/")
    service_port = int(os.getenv("SERVICE_PORT", "8086"))
    service_host = os.getenv("SERVICE_HOST", "localhost")
    await eureka_client.init_async(
        eureka_server=eureka_server,
        app_name="notes-service",
        instance_port=service_port,
        instance_host=service_host
    )

# ── Helper ──
def get_student_id(x_user_id: Optional[str] = Header(None)) -> int:
    if not x_user_id:
        raise HTTPException(status_code=401, detail="X-User-Id header required")
    try:
        return int(x_user_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid X-User-Id")

# ── CRUD Endpoints ──

@app.get("/api/notes/directories", response_model=List[str])
def get_directories(
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    results = db.query(models.Note.directory).filter(
        models.Note.student_id == student_id,
        models.Note.is_archived == False
    ).distinct().all()
    dirs = [r[0] for r in results]
    if "General" not in dirs:
        dirs.insert(0, "General")
    return dirs

@app.get("/api/notes/search", response_model=List[schemas.NoteResponse])
def search_notes(
    q: str,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    return db.query(models.Note).filter(
        models.Note.student_id == student_id,
        models.Note.is_archived == False,
        (models.Note.title.ilike(f"%{q}%") | models.Note.content.ilike(f"%{q}%"))
    ).order_by(models.Note.updated_at.desc()).all()

@app.get("/api/notes", response_model=List[schemas.NoteResponse])
def get_notes(
    directory: Optional[str] = None,
    include_archived: bool = False,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    query = db.query(models.Note).filter(models.Note.student_id == student_id)
    if not include_archived:
        query = query.filter(models.Note.is_archived == False)
    if directory:
        query = query.filter(models.Note.directory == directory)
    return query.order_by(models.Note.is_pinned.desc(), models.Note.updated_at.desc()).all()

@app.post("/api/notes", response_model=schemas.NoteResponse, status_code=201)
def create_note(
    note: schemas.NoteCreate,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    db_note = models.Note(**note.model_dump(), student_id=student_id)
    db.add(db_note)
    db.commit()
    db.refresh(db_note)
    return db_note

@app.put("/api/notes/{note_id}", response_model=schemas.NoteResponse)
def update_note(
    note_id: int,
    note: schemas.NoteUpdate,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    db_note = db.query(models.Note).filter(
        models.Note.id == note_id,
        models.Note.student_id == student_id
    ).first()
    if not db_note:
        raise HTTPException(status_code=404, detail="Note not found")
    for field, value in note.model_dump(exclude_unset=True).items():
        setattr(db_note, field, value)
    db.commit()
    db.refresh(db_note)
    return db_note

@app.delete("/api/notes/{note_id}", status_code=204)
def delete_note(
    note_id: int,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    db_note = db.query(models.Note).filter(
        models.Note.id == note_id,
        models.Note.student_id == student_id
    ).first()
    if not db_note:
        raise HTTPException(status_code=404, detail="Note not found")
    db.delete(db_note)
    db.commit()

@app.patch("/api/notes/{note_id}/pin", response_model=schemas.NoteResponse)
def toggle_pin(
    note_id: int,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    db_note = db.query(models.Note).filter(
        models.Note.id == note_id,
        models.Note.student_id == student_id
    ).first()
    if not db_note:
        raise HTTPException(status_code=404, detail="Note not found")
    db_note.is_pinned = not db_note.is_pinned
    db.commit()
    db.refresh(db_note)
    return db_note

@app.patch("/api/notes/{note_id}/archive", response_model=schemas.NoteResponse)
def toggle_archive(
    note_id: int,
    db: Session = Depends(get_db),
    student_id: int = Depends(get_student_id)
):
    db_note = db.query(models.Note).filter(
        models.Note.id == note_id,
        models.Note.student_id == student_id
    ).first()
    if not db_note:
        raise HTTPException(status_code=404, detail="Note not found")
    db_note.is_archived = not db_note.is_archived
    db.commit()
    db.refresh(db_note)
    return db_note

@app.get("/health")
def health():
    return {"status": "ok", "service": "notes-service"}
