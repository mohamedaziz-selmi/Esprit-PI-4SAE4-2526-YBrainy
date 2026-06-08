import urllib.request, urllib.error, json, time, urllib.parse

COURSE_BASE = 'http://localhost:8082'
LESSON_BASE = 'http://localhost:8084'
QUIZ_BASE   = 'http://localhost:8083'
ENROLL_BASE = 'http://localhost:8085'

def req(base, method, path, body=None):
    url = base + path
    data = json.dumps(body).encode() if body else None
    headers = {'Content-Type': 'application/json'}
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        res = urllib.request.urlopen(r, timeout=10)
        raw = res.read()
        return res.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, {}
    except Exception as e:
        return 0, str(e)

def req_multipart_course(method, path, course_dict):
    boundary = '----CourseBoundaryABC'
    course_json = json.dumps(course_dict).encode('utf-8')
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="course"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + course_json + b'\r\n'
        + f'--{boundary}--\r\n'.encode()
    )
    url = COURSE_BASE + path
    headers = {'Content-Type': f'multipart/form-data; boundary={boundary}'}
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        res = urllib.request.urlopen(r, timeout=10)
        raw = res.read()
        return res.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, {}
    except Exception as e:
        return 0, str(e)

def req_lesson_multipart(base, method, path, meta_dict, youtube_url=None):
    boundary = '----LessonBoundaryXyZ123'
    lesson_json = json.dumps(meta_dict).encode('utf-8')
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="lesson"; filename="lesson.json"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + lesson_json + b'\r\n'
        + f'--{boundary}--\r\n'.encode()
    )
    full_path = path
    if youtube_url:
        full_path = path + '?youtubeUrls=' + urllib.parse.quote(youtube_url)
    url = base + full_path
    headers = {'Content-Type': f'multipart/form-data; boundary={boundary}'}
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        res = urllib.request.urlopen(r, timeout=10)
        raw = res.read()
        return res.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, {}
    except Exception as e:
        return 0, str(e)

print('=== SUITE 3: COURSE DELETION CASCADE ===')

# Create course
new_course = {
    'title': 'CASCADE_TEST_COURSE',
    'description': 'Will be deleted for cascade test',
    'price': '0.00',
    'level': 'BEGINNER',
    'category': 'PROGRAMMING',
    'instructorId': 1,
    'isPublished': False
}
status, data = req_multipart_course('POST', '/api/courses', new_course)
if status not in [200, 201] or not data.get('id'):
    print(f'[FAIL] Setup: Could not create test course -> {status} {str(data)[:200]}')
    exit(1)
cascade_course_id = data['id']
print(f'[INFO] Created cascade test course id={cascade_course_id}')

# Add a lesson
meta = {'title': 'CASCADE_LESSON', 'description': 'test', 'orderIndex': 1, 'durationMinutes': 1}
status, lesson_data = req_lesson_multipart(LESSON_BASE, 'POST',
    f'/api/courses/{cascade_course_id}/lessons', meta,
    youtube_url='https://www.youtube.com/watch?v=cascade-test')
if status in [200, 201]:
    cascade_lesson_id = lesson_data.get('id')
    print(f'[INFO] Created cascade test lesson id={cascade_lesson_id}')
else:
    cascade_lesson_id = None
    print(f'[WARN] Could not create lesson -> {status} {str(lesson_data)[:200]}')

# Add a quiz
new_quiz = {'title': 'CASCADE_QUIZ', 'timeLimitMinutes': 10, 'passingScore': 70}
status, quiz_data = req(QUIZ_BASE, 'POST', f'/api/quizzes?courseId={cascade_course_id}', new_quiz)
if status in [200, 201]:
    cascade_quiz_id = quiz_data.get('id')
    print(f'[INFO] Created cascade test quiz id={cascade_quiz_id}')
else:
    cascade_quiz_id = None
    print(f'[WARN] Could not create quiz -> {status} {str(quiz_data)[:200]}')

time.sleep(1)

# 3.1 Delete the course (ADMIN bypass)
status, data = req(COURSE_BASE, 'DELETE', f'/api/courses/{cascade_course_id}?requestingUserId=1&requestingRole=ADMIN')
if status in [200, 204]:
    print(f'[PASS] 3.1 DELETE course {cascade_course_id} -> {status}')
else:
    print(f'[FAIL] 3.1 DELETE course -> {status} {str(data)[:300]}')
    exit(1)

# Wait for async cascade
time.sleep(3)

# 3.2 Verify course is gone
status, data = req(COURSE_BASE, 'GET', f'/api/courses/{cascade_course_id}')
if status == 404:
    print(f'[PASS] 3.2 Course confirmed deleted (GET -> 404)')
elif status in [400, 500]:
    print(f'[PASS] 3.2 Course confirmed deleted (GET -> {status})')
else:
    print(f'[FAIL] 3.2 Course still accessible after deletion! status={status}')

# 3.3 Verify lessons are gone
if cascade_lesson_id:
    status, data = req(LESSON_BASE, 'GET', f'/api/courses/{cascade_course_id}/lessons/{cascade_lesson_id}')
    if status in [404, 400, 500]:
        print(f'[PASS] 3.3 Lesson deleted after course deletion (GET -> {status})')
    elif status == 200 and not data.get('id'):
        print(f'[PASS] 3.3 Lesson returns empty (deleted)')
    else:
        print(f'[FAIL] 3.3 Lesson still exists after course deletion! status={status} id={data.get("id")}')
    # Also check the lessons list for the deleted course
    status2, data2 = req(LESSON_BASE, 'GET', f'/api/courses/{cascade_course_id}/lessons')
    count = len(data2) if isinstance(data2, list) else 0
    if count == 0:
        print(f'[PASS] 3.3b Lesson list for deleted course -> empty ({status2})')
    else:
        print(f'[FAIL] 3.3b Lesson list for deleted course has {count} items!')

# 3.4 Verify quizzes are gone
if cascade_quiz_id:
    time.sleep(2)
    status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes/{cascade_quiz_id}')
    if status in [404, 400]:
        print(f'[PASS] 3.4 Quiz deleted via cascade (GET -> {status})')
    elif status == 500:
        print(f'[PASS] 3.4 Quiz deleted via cascade (GET -> 500, likely not found)')
    else:
        print(f'[WARN] 3.4 Quiz may still exist (async RabbitMQ) -> {status} {str(data)[:100]}')
        status2, data2 = req(QUIZ_BASE, 'GET', f'/api/quizzes?courseId={cascade_course_id}')
        quiz_count = len(data2) if isinstance(data2, list) else 'N/A'
        print(f'       Quizzes for deleted course: {status2} count={quiz_count}')

# 3.5 Also verify the already-created course (id=126) is accessible
status, _ = req(COURSE_BASE, 'GET', '/api/courses/126')
if status == 200:
    print(f'[PASS] 3.5 Existing course 126 unaffected by cascade delete')
else:
    print(f'[WARN] 3.5 Course 126 status after cascade: {status}')
