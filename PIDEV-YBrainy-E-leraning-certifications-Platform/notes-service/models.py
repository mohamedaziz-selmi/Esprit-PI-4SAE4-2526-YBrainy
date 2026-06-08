from sqlalchemy import Column, Integer, String, Text, Boolean, DateTime, func
from database import Base

NOTE_COLORS = ['#ffffff', '#fef9c3', '#dcfce7', '#dbeafe', '#fce7f3', '#f3e8ff', '#ffedd5']

class Note(Base):
    __tablename__ = "notes"

    id = Column(Integer, primary_key=True, index=True)
    student_id = Column(Integer, nullable=False, index=True)
    title = Column(String(200), nullable=False)
    content = Column(Text, nullable=True)
    directory = Column(String(100), nullable=False, default="General")
    color = Column(String(20), nullable=False, default="#ffffff")
    is_pinned = Column(Boolean, default=False)
    is_archived = Column(Boolean, default=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now(), server_default=func.now())
