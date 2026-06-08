import urllib.request, urllib.error, json

COURSE_BASE = 'http://localhost:8082'
QUIZ_BASE   = 'http://localhost:8083'

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

print('=== SUITE 4: QUIZ CRUD ===')

# Get a course
status, data = req(COURSE_BASE, 'GET', '/api/courses')
courses = data['content'] if isinstance(data, dict) else data
course_id = courses[0]['id'] if courses else None
if not course_id:
    print('[FAIL] Setup: No courses available')
    exit(1)
print(f'[INFO] Using course_id={course_id}')

# 4.1 Get quizzes for course
status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes?courseId={course_id}')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 4.1 GET /api/quizzes?courseId={course_id} -> {len(data)} quizzes')
    first_quiz = data[0] if data else None
    for q in data[:3]:
        print(f'       Quiz id={q["id"]} title="{q.get("title","")}" questions={q.get("totalQuestions",0)}')
else:
    print(f'[FAIL] 4.1 GET quizzes -> {status} {str(data)[:200]}')
    first_quiz = None

# 4.2 Get all quizzes (no filter)
status, data = req(QUIZ_BASE, 'GET', '/api/quizzes')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 4.2 GET /api/quizzes (all) -> {len(data)} quizzes total')
else:
    print(f'[INFO] 4.2 GET /api/quizzes (no courseId) -> {status} {str(data)[:100]}')

# 4.3 Create quiz
new_quiz = {
    'title': 'TEST_QUIZ_DELETE_ME',
    'timeLimitMinutes': 30,
    'passingScore': 70,
    'maxAttempts': 3
}
status, data = req(QUIZ_BASE, 'POST', f'/api/quizzes?courseId={course_id}', new_quiz)
if status in [200, 201] and data.get('id'):
    test_quiz_id = data['id']
    print(f'[PASS] 4.3 POST /api/quizzes?courseId={course_id} -> id={test_quiz_id}')
else:
    print(f'[FAIL] 4.3 POST create quiz -> {status} {str(data)[:300]}')
    test_quiz_id = None

# 4.4 Feign validation: quiz for non-existent course
status, data = req(QUIZ_BASE, 'POST', '/api/quizzes?courseId=999999', new_quiz)
if status in [400, 404, 500]:
    print(f'[PASS] 4.4 Feign validation: quiz for non-existent course -> {status} (rejected)')
else:
    print(f'[WARN] 4.4 Feign validation: quiz for non-existent course allowed! -> {status}')

# 4.5 Get single quiz
if test_quiz_id:
    status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes/{test_quiz_id}')
    if status == 200 and data.get('id') == test_quiz_id:
        print(f'[PASS] 4.5 GET /api/quizzes/{test_quiz_id} -> title="{data.get("title")}" timeLimitMinutes={data.get("timeLimitMinutes")}')
    else:
        print(f'[FAIL] 4.5 GET quiz -> {status} {str(data)[:200]}')

# 4.6 Update quiz
if test_quiz_id:
    update = {
        'title': 'TEST_QUIZ_UPDATED',
        'timeLimitMinutes': 45,
        'passingScore': 80,
        'maxAttempts': 5
    }
    status, data = req(QUIZ_BASE, 'PUT', f'/api/quizzes/{test_quiz_id}', update)
    if status in [200, 201]:
        status2, data2 = req(QUIZ_BASE, 'GET', f'/api/quizzes/{test_quiz_id}')
        if data2.get('title') == 'TEST_QUIZ_UPDATED':
            print(f'[PASS] 4.6 UPDATE quiz -> title persisted, timeLimitMinutes={data2.get("timeLimitMinutes")}')
        else:
            print(f'[FAIL] 4.6 UPDATE -> title still={data2.get("title")}')
    else:
        print(f'[FAIL] 4.6 PUT quiz -> {status} {str(data)[:200]}')

# 4.7 Add question to quiz
if test_quiz_id:
    new_question = {
        'questionText': 'What is 2+2?',
        'orderIndex': 1,
        'points': 10,
        'options': [
            {'optionText': '3', 'isCorrect': False, 'orderIndex': 1},
            {'optionText': '4', 'isCorrect': True,  'orderIndex': 2},
            {'optionText': '5', 'isCorrect': False, 'orderIndex': 3},
        ]
    }
    status, data = req(QUIZ_BASE, 'POST', f'/api/quizzes/{test_quiz_id}/questions', new_question)
    if status in [200, 201] and data.get('id'):
        test_question_id = data['id']
        print(f'[PASS] 4.7 POST add question -> id={test_question_id} points={data.get("points")}')
    else:
        print(f'[FAIL] 4.7 POST add question -> {status} {str(data)[:200]}')
        test_question_id = None

# 4.8 Get questions with options
if test_quiz_id:
    status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes/{test_quiz_id}/questions')
    if status == 200 and isinstance(data, list):
        print(f'[PASS] 4.8 GET questions -> {len(data)} questions')
        for q in data:
            opts = q.get('options') or []
            correct = [o for o in opts if o.get('isCorrect') or o.get('correct')]
            print(f'       Q: "{q.get("questionText","")[:40]}" | {len(opts)} options | {len(correct)} correct | {q.get("points",0)} pts')
    else:
        print(f'[FAIL] 4.8 GET questions -> {status} {str(data)[:200]}')

# 4.9 Submit without enrollment (should be blocked)
if test_quiz_id:
    status, data = req(QUIZ_BASE, 'POST', f'/api/quizzes/{test_quiz_id}/submit?studentId=999',
        {'answers': []})
    if status in [400, 403, 500]:
        msg = data.get('message', data.get('error', str(data)[:60]))
        print(f'[PASS] 4.9 Submit without enrollment -> {status} ("{msg[:60]}")')
    else:
        print(f'[WARN] 4.9 Submit without enrollment -> {status} (should be blocked)')

# 4.10 Leaderboard
if first_quiz:
    status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes/{first_quiz["id"]}/leaderboard')
    if status == 200 and isinstance(data, list):
        print(f'[PASS] 4.10 GET leaderboard for quiz {first_quiz["id"]} -> {len(data)} entries')
        for entry in data[:3]:
            print(f'       #{entry.get("rank","?")} {entry.get("studentName","?")} score={entry.get("score","?")}')
    else:
        print(f'[FAIL] 4.10 Leaderboard -> {status} {str(data)[:200]}')
else:
    print(f'[INFO] 4.10 No existing quiz to check leaderboard')

# 4.11 Delete test quiz
if test_quiz_id:
    status, data = req(QUIZ_BASE, 'DELETE', f'/api/quizzes/{test_quiz_id}')
    if status in [200, 204]:
        status2, _ = req(QUIZ_BASE, 'GET', f'/api/quizzes/{test_quiz_id}')
        if status2 in [404, 400, 500]:
            print(f'[PASS] 4.11 DELETE quiz -> confirmed gone (GET -> {status2})')
        else:
            print(f'[FAIL] 4.11 Quiz still accessible after delete! GET -> {status2}')
    else:
        print(f'[FAIL] 4.11 DELETE quiz -> {status} {str(data)[:200]}')

print(f'course_id={course_id}')
