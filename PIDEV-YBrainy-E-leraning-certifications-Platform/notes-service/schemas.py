from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class NoteCreate(BaseModel):
    title: str
    content: Optional[str] = ""
    directory: Optional[str] = "General"
    color: Optional[str] = "#ffffff"
    is_pinned: Optional[bool] = False

class NoteUpdate(BaseModel):
    title: Optional[str] = None
    content: Optional[str] = None
    directory: Optional[str] = None
    color: Optional[str] = None
    is_pinned: Optional[bool] = None
    is_archived: Optional[bool] = None

class NoteResponse(BaseModel):
    id: int
    student_id: int
    title: str
    content: Optional[str]
    directory: str
    color: str
    is_pinned: bool
    is_archived: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
