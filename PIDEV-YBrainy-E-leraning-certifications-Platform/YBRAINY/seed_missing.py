"""
Seeds only the 30 courses that failed due to offersCertificate requiring a file upload.
"""
import sys
sys.path.insert(0, r"C:\Users\azizs\OneDrive\Desktop\YBRAINY")

# Import the helpers and data from the main script
from seed_courses import (
    COURSES, YOUTUBE_URLS,
    create_course, create_lesson, create_quiz, add_question,
    enroll_student, add_review, REVIEW_COMMENTS_BY_CAT,
)

import random
random.seed(99)

FAILED_TITLES = {
    "Python Data Analysis",
    "Docker & Containerization",
    "Data Science with Python",
    "React.js Complete Guide",
    "Java Spring Boot",
    "Machine Learning Fundamentals",
    "Deep Learning with TensorFlow",
    "TypeScript Mastery",
    "Algorithms & Data Structures",
    "Cloud Computing AWS",
    "DevOps CI/CD Pipelines",
    "Adobe Photoshop Mastery",
    "3D Modeling with Blender",
    "Motion Graphics with After Effects",
    "Brand Identity Design",
    "Project Management Professional",
    "Business Analytics",
    "Supply Chain Management",
    "Business Strategy",
    "Google Ads Mastery",
    "Digital Marketing Complete",
    "Neuroscience Introduction",
    "Quantum Physics Basics",
    "Statistics for Data Science",
    "Number Theory",
    "Differential Equations",
    "Applied Mathematics",
    "English Business Writing",
    "Music Production with FL Studio",
    "Street Photography Masterclass",
}

failed_courses = [c for c in COURSES if c["title"] in FAILED_TITLES]
print(f"Re-seeding {len(failed_courses)} failed courses...\n")

created_ids = []
created_cats = []

for idx, course_data in enumerate(failed_courses):
    title = course_data["title"]
    cat   = course_data["category"]
    print(f"[{idx+1:02d}/{len(failed_courses)}] {title} ({cat})")

    resp = create_course(course_data, idx)
    if resp.status_code not in (200, 201):
        print(f"  ERROR: {resp.status_code} {resp.text[:200]}")
        continue

    course_id = resp.json().get("id") or resp.json().get("courseId")
    if not course_id:
        print(f"  ERROR: no id: {resp.text[:200]}")
        continue

    created_ids.append(course_id)
    created_cats.append(cat)
    print(f"  Course ID: {course_id}")

    topic = course_data.get("topic", title)
    lessons = [
        (f"Introduction to {topic}", f"Get a solid overview of {topic}, understand its core concepts and why it matters in today's world.", 0, 35),
        (f"Core Concepts of {topic}", f"Dive into the fundamental building blocks of {topic} through clear explanations and worked examples.", 1, 55),
        (f"Practical {topic} — Hands On", f"Apply what you have learned in hands-on exercises and real projects. Theory becomes practice.", 2, 75),
        (f"Advanced {topic} Techniques", f"Level up with advanced techniques and best practices used by professionals.", 3, 60),
    ]
    for l_idx, (l_title, l_desc, l_order, l_dur) in enumerate(lessons):
        yt_url = YOUTUBE_URLS[l_idx % len(YOUTUBE_URLS)]
        l_resp = create_lesson(course_id, l_title, l_desc, l_order, l_dur, yt_url)
        if l_resp.status_code not in (200, 201):
            print(f"  WARN lesson {l_idx+1}: {l_resp.status_code} {l_resp.text[:100]}")
        else:
            print(f"  Lesson {l_idx+1} ok")

    q_resp = create_quiz(course_id, f"{title} Assessment")
    if q_resp.status_code not in (200, 201):
        print(f"  WARN quiz: {q_resp.status_code} {q_resp.text[:200]}")
    else:
        quiz_id = q_resp.json().get("id") or q_resp.json().get("quizId")
        print(f"  Quiz ID: {quiz_id}")
        for q_def in course_data.get("questions", []):
            qr = add_question(quiz_id, q_def["q"], q_def["options"], q_def["correct"])
            if qr.status_code not in (200, 201):
                print(f"  WARN q: {qr.status_code}")
        print(f"  {len(course_data.get('questions', []))} questions added")
    print()

print(f"Created {len(created_ids)} additional courses.")

# Add enrollments for students 1 and 2 in some of the new courses
print("\nAdding enrollments for new courses...")
enroll_for_1 = created_ids[0::3][:5]
enroll_for_2 = created_ids[1::3][:5]
for cid in enroll_for_1:
    enroll_student(1, cid)
for cid in enroll_for_2:
    enroll_student(2, cid)
print(f"  Enrolled student 1 in {len(enroll_for_1)}, student 2 in {len(enroll_for_2)} new courses")

# Add reviews
print("\nAdding reviews for new courses...")
review_count = 0
for i, cid in enumerate(created_ids[:20]):
    cat = created_cats[i] if i < len(created_cats) else "PROGRAMMING"
    comments = REVIEW_COMMENTS_BY_CAT.get(cat, REVIEW_COMMENTS_BY_CAT["PROGRAMMING"])
    n = random.randint(2, 3)
    for r_idx in range(n):
        sid = (r_idx % 2) + 1
        rating = random.randint(3, 5)
        comment = comments[r_idx % len(comments)]
        rr = add_review(cid, sid, rating, comment)
        if rr.status_code in (200, 201):
            review_count += 1
print(f"  Added {review_count} reviews")

print("\n" + "="*60)
print("ADDITIONAL SEEDING COMPLETE")
print("="*60)
print(f"New courses: {len(created_ids)}")

from collections import Counter
print("\nNew courses by category:")
for cat, count in sorted(Counter(created_cats).items()):
    print(f"  {cat}: {count}")
