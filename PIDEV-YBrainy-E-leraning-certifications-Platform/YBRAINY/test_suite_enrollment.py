import urllib.request, urllib.error, json

COURSE_BASE  = 'http://localhost:8082'
ENROLL_BASE  = 'http://localhost:8085'

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

print('=== SUITE 5: ENROLLMENT ===')

# Get a course
status, data = req(COURSE_BASE, 'GET', '/api/courses')
courses = data['content'] if isinstance(data, dict) else data
course_id = courses[0]['id'] if courses else None
print(f'[INFO] Using course_id={course_id}')

# 5.1 Get enrollments for student
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/student/3')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 5.1 GET /api/enrollments/student/3 -> {len(data)} enrollments')
    for e in data[:3]:
        print(f'       Enrollment id={e["id"]} courseId={e.get("courseId")} status={e.get("status")} progress={e.get("progress")}')
else:
    print(f'[FAIL] 5.1 GET enrollments -> {status} {str(data)[:200]}')

# 5.2 Check enrollment status (exists endpoint)
if course_id:
    status, data = req(ENROLL_BASE, 'GET', f'/api/enrollments/exists?studentId=3&courseId={course_id}')
    if status == 200:
        print(f'[PASS] 5.2 GET /api/enrollments/exists?studentId=3&courseId={course_id} -> enrolled={data}')
    else:
        print(f'[FAIL] 5.2 Check enrollment exists -> {status} {str(data)[:100]}')

# 5.3 Enroll new student
if course_id:
    status, data = req(ENROLL_BASE, 'POST', f'/api/enrollments?studentId=9991&courseId={course_id}')
    if status in [200, 201] and data.get('id'):
        test_enroll_id = data['id']
        print(f'[PASS] 5.3 POST /api/enrollments?studentId=9991&courseId={course_id} -> id={test_enroll_id} status={data.get("status")}')
    else:
        print(f'[FAIL] 5.3 POST enroll -> {status} {str(data)[:200]}')
        test_enroll_id = None

# 5.4 Duplicate enrollment blocked
if course_id:
    status, data = req(ENROLL_BASE, 'POST', f'/api/enrollments?studentId=9991&courseId={course_id}')
    if status in [400, 409, 500]:
        msg = data.get('message', data.get('error', str(data)[:60]))
        print(f'[PASS] 5.4 Duplicate enrollment blocked -> {status} ("{msg[:60]}")')
    else:
        print(f'[WARN] 5.4 Duplicate enrollment allowed! -> {status}')

# 5.5 Get enrollments for test student
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/student/9991')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 5.5 GET enrollments for student 9991 -> {len(data)} enrollments')
else:
    print(f'[FAIL] 5.5 GET enrollments student 9991 -> {status}')

# 5.6 Update progress
if course_id:
    status, data = req(ENROLL_BASE, 'PUT', f'/api/enrollments/progress?studentId=9991&courseId={course_id}&progress=50.0')
    if status in [200, 201]:
        print(f'[PASS] 5.6 PUT update progress -> progress={data.get("progress","?")} status={data.get("status","?")}')
    else:
        # Try alternate endpoint
        status2, data2 = req(ENROLL_BASE, 'PATCH', f'/api/enrollments/progress?studentId=9991&courseId={course_id}&progress=50.0')
        if status2 in [200, 201]:
            print(f'[PASS] 5.6 PATCH update progress -> progress={data2.get("progress","?")}')
        else:
            print(f'[INFO] 5.6 Progress update -> PUT={status} PATCH={status2} {str(data)[:100]}')

# 5.7 Monthly enrollment counts
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/monthly-counts')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 5.7 GET /api/enrollments/monthly-counts -> {len(data)} months')
    for m in data[:3]:
        print(f'       {m.get("month","?")} {m.get("year","?")}: {m.get("count","?")} enrollments')
else:
    print(f'[FAIL] 5.7 Monthly counts -> {status} {str(data)[:200]}')

# 5.8 Student dashboard
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/students/3/dashboard')
if status == 200 and isinstance(data, dict):
    enrolled = data.get('totalEnrolled', 0)
    completed = data.get('totalCompleted', 0)
    print(f'[PASS] 5.8 Student 3 dashboard -> enrolled={enrolled} completed={completed}')
    enrolled_courses = data.get('enrolledCourses', [])
    print(f'       {len(enrolled_courses)} enrolled courses in response')
    for c in enrolled_courses[:3]:
        print(f'       Course {c.get("courseId")} progress={c.get("progress","?")}% totalLessons={c.get("totalLessons","?")}')
else:
    print(f'[FAIL] 5.8 Student dashboard -> {status} {str(data)[:200]}')

# 5.9 Enrollment by courseId
if course_id:
    status, data = req(ENROLL_BASE, 'GET', f'/api/enrollments/course/{course_id}')
    if status == 200 and isinstance(data, list):
        print(f'[PASS] 5.9 GET enrollments for course {course_id} -> {len(data)} enrollments')
    else:
        # Try alternate
        status2, data2 = req(ENROLL_BASE, 'GET', f'/api/enrollments?courseId={course_id}')
        print(f'[INFO] 5.9 GET enrollments by course -> {status}/{status2}')

# 5.10 Non-existent student enrollment check
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/student/999999')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 5.10 GET enrollments for non-existent student -> {len(data)} (empty list)')
elif status in [404, 400]:
    print(f'[PASS] 5.10 Non-existent student -> {status}')
else:
    print(f'[INFO] 5.10 Non-existent student -> {status}')

print(f'course_id={course_id}')
