import urllib.request, urllib.error, json, urllib.parse

COURSE_BASE  = 'http://localhost:8082'
LESSON_BASE  = 'http://localhost:8084'
QUIZ_BASE    = 'http://localhost:8083'
ENROLL_BASE  = 'http://localhost:8085'
GATEWAY      = 'http://localhost:8088'

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

def req_multipart_course(course_dict):
    boundary = '----CourseBoundaryABC'
    course_json = json.dumps(course_dict).encode('utf-8')
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="course"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + course_json + b'\r\n'
        + f'--{boundary}--\r\n'.encode()
    )
    url = COURSE_BASE + '/api/courses'
    headers = {'Content-Type': f'multipart/form-data; boundary={boundary}'}
    r = urllib.request.Request(url, data=body, headers=headers, method='POST')
    try:
        res = urllib.request.urlopen(r, timeout=10)
        raw = res.read()
        return res.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, {}
    except Exception as e:
        return 0, str(e)

print('=== SUITE 7: EDGE CASES ===')

# 7.1 Lessons for non-existent course
status, data = req(LESSON_BASE, 'GET', '/api/courses/999999/lessons')
if status == 200 and isinstance(data, list) and len(data) == 0:
    print(f'[PASS] 7.1 Lessons for non-existent course -> empty list (graceful)')
elif status in [404, 400]:
    print(f'[PASS] 7.1 Lessons for non-existent course -> {status}')
else:
    print(f'[INFO] 7.1 Lessons for non-existent course -> {status} {str(data)[:100]}')

# 7.2 Delete non-existent course
status, data = req(COURSE_BASE, 'DELETE', '/api/courses/999999?requestingUserId=1&requestingRole=ADMIN')
if status in [404, 400, 500]:
    print(f'[PASS] 7.2 DELETE non-existent course -> {status}')
else:
    print(f'[WARN] 7.2 DELETE non-existent course -> {status}')

# 7.3 Create course with empty title (validation)
status, data = req_multipart_course({'title': '', 'description': 'test', 'price': '0', 'level': 'BEGINNER', 'category': 'PROGRAMMING'})
if status in [400, 500]:
    msg = data.get('message', data.get('error', str(data)[:60]))
    print(f'[PASS] 7.3 Create course with empty title -> {status} (validation)')
else:
    print(f'[WARN] 7.3 Create course with empty title allowed! -> {status}')

# 7.4 Create course with negative price (validation)
status, data = req_multipart_course({'title': 'BAD_PRICE', 'description': 'test', 'price': '-10', 'level': 'BEGINNER', 'category': 'PROGRAMMING'})
if status in [400, 500]:
    print(f'[PASS] 7.4 Create course with negative price -> {status} (validation rejected)')
else:
    print(f'[WARN] 7.4 Negative price course created! -> {status}')

# 7.5 Get course with invalid ID format
status, data = req(COURSE_BASE, 'GET', '/api/courses/not-a-number')
if status in [400, 404, 500]:
    print(f'[PASS] 7.5 GET course with non-numeric ID -> {status}')
else:
    print(f'[WARN] 7.5 Non-numeric course ID -> {status}')

# 7.6 Lesson update preserves contents (the original bug)
status, courses = req(COURSE_BASE, 'GET', '/api/courses')
all_courses = courses['content'] if isinstance(courses, dict) else courses
test_cid = None
test_lid = None
test_orig_content = None
for c in all_courses:
    s, ls = req(LESSON_BASE, 'GET', f'/api/courses/{c["id"]}/lessons')
    if s == 200 and ls:
        for l in ls:
            s2, ld = req(LESSON_BASE, 'GET', f'/api/courses/{c["id"]}/lessons/{l["id"]}')
            if s2 == 200 and ld.get('contentUrl'):
                test_cid = c['id']
                test_lid = l['id']
                test_orig_content = {
                    'url': ld.get('contentUrl'),
                    'type': ld.get('type'),
                    'title': ld.get('title')
                }
                break
        if test_lid:
            break

if test_lid:
    print(f'[INFO] 7.6 Testing update on lesson {test_lid} (course {test_cid}), type={test_orig_content["type"]}')
    # Update only the title via the internal /api/lessons endpoint (which takes JSON body)
    status, orig = req(LESSON_BASE, 'GET', f'/api/courses/{test_cid}/lessons/{test_lid}')
    orig_url = orig.get('contentUrl', '')
    orig_type = orig.get('type', '')

    # Use /api/lessons/course/{courseId}/lesson/{lessonId} which accepts JSON body
    update_body = {
        'title': orig.get('title', '') + '_UPDATED',
        'description': orig.get('description', ''),
        'orderIndex': orig.get('orderIndex', 1),
        'type': orig_type,
        'contentUrl': orig_url,
        'courseId': test_cid
    }
    status2, upd = req(LESSON_BASE, 'PUT', f'/api/lessons/course/{test_cid}/lesson/{test_lid}', update_body)
    if status2 in [200, 201]:
        status3, after = req(LESSON_BASE, 'GET', f'/api/courses/{test_cid}/lessons/{test_lid}')
        after_url = after.get('contentUrl', '')
        if after_url == orig_url:
            print(f'[PASS] 7.6 Lesson title update via JSON endpoint preserves contentUrl: {orig_url[:50]}')
        else:
            print(f'[FAIL] 7.6 contentUrl changed after update! before={orig_url[:50]} after={after_url[:50]}')
        # Restore original title
        update_body['title'] = orig.get('title', '')
        req(LESSON_BASE, 'PUT', f'/api/lessons/course/{test_cid}/lesson/{test_lid}', update_body)
    else:
        print(f'[INFO] 7.6 JSON lesson update -> {status2} {str(upd)[:100]}')
else:
    print(f'[WARN] 7.6 No lesson with contentUrl found for update test')

# 7.7 Quiz with non-existent quiz ID
status, data = req(QUIZ_BASE, 'GET', '/api/quizzes/999999')
if status in [404, 400, 500]:
    print(f'[PASS] 7.7 GET non-existent quiz -> {status}')
else:
    print(f'[WARN] 7.7 GET non-existent quiz -> {status}')

# 7.8 Enrollment with non-existent course
status, data = req(ENROLL_BASE, 'POST', '/api/enrollments?studentId=9999&courseId=999999')
if status in [400, 404, 500]:
    msg = data.get('message', data.get('error', str(data)[:60]))
    print(f'[PASS] 7.8 Enroll in non-existent course -> {status} (rejected)')
else:
    print(f'[WARN] 7.8 Enroll in non-existent course allowed! -> {status}')

# 7.9 Monthly counts null handling
status, data = req(ENROLL_BASE, 'GET', '/api/enrollments/monthly-counts')
if status == 200:
    null_months = [m for m in data if m.get('month') is None or m.get('year') is None]
    valid_months = [m for m in data if m.get('month') and m.get('year')]
    if null_months:
        print(f'[WARN] 7.9 Monthly counts has {len(null_months)} entries with null month/year (data quality issue)')
    else:
        print(f'[PASS] 7.9 Monthly counts: {len(valid_months)} clean entries, no null months')
    for m in data[:4]:
        print(f'       month={m.get("month","NULL")} year={m.get("year","NULL")} count={m.get("count",0)}')

# 7.10 Course pagination
status, data = req(COURSE_BASE, 'GET', '/api/courses?page=0&size=3')
if status == 200 and isinstance(data, dict):
    content = data.get('content', [])
    total = data.get('totalElements', 0)
    total_pages = data.get('totalPages', 0)
    print(f'[PASS] 7.10 Course pagination: page=0 size=3 -> {len(content)} items, total={total}, pages={total_pages}')
    # Try page 2
    status2, data2 = req(COURSE_BASE, 'GET', '/api/courses?page=1&size=3')
    if status2 == 200:
        ids1 = {c['id'] for c in content}
        ids2 = {c['id'] for c in data2.get('content', [])}
        overlap = ids1 & ids2
        if not overlap:
            print(f'[PASS] 7.10b Pages are non-overlapping (no duplicate items)')
        else:
            print(f'[FAIL] 7.10b Pages overlap! Duplicate IDs: {overlap}')
else:
    print(f'[FAIL] 7.10 Course pagination -> {status}')

# 7.11 Course filter by category
status, data = req(COURSE_BASE, 'GET', '/api/courses?category=PROGRAMMING&size=50')
if status == 200 and isinstance(data, dict):
    content = data['content']
    non_programming = [c for c in content if c.get('category') != 'PROGRAMMING']
    if not non_programming:
        print(f'[PASS] 7.11 Filter by category PROGRAMMING -> {len(content)} courses, all correct category')
    else:
        print(f'[FAIL] 7.11 Filter contaminated: {len(non_programming)} non-PROGRAMMING courses in results')
else:
    print(f'[FAIL] 7.11 Category filter -> {status}')

# 7.12 Course filter by level
status, data = req(COURSE_BASE, 'GET', '/api/courses?level=BEGINNER&size=50')
if status == 200 and isinstance(data, dict):
    content = data['content']
    non_beginner = [c for c in content if c.get('level') != 'BEGINNER']
    if not non_beginner:
        print(f'[PASS] 7.12 Filter by level BEGINNER -> {len(content)} courses, all correct level')
    else:
        print(f'[FAIL] 7.12 Level filter contaminated: {len(non_beginner)} non-BEGINNER courses')
else:
    print(f'[FAIL] 7.12 Level filter -> {status}')

# 7.13 Course search
status, data = req(COURSE_BASE, 'GET', '/api/courses?search=python&size=20')
if status == 200 and isinstance(data, dict):
    content = data['content']
    print(f'[PASS] 7.13 Course search "python" -> {len(content)} results')
    for c in content[:3]:
        print(f'       "{c.get("title")}"')
else:
    print(f'[FAIL] 7.13 Course search -> {status}')

# Clean up FEIGN_TEST_REAL quiz from suite 6
print()
print('=== CLEANUP ===')
status, data = req(QUIZ_BASE, 'GET', f'/api/quizzes?courseId=126')
if status == 200 and isinstance(data, list):
    for q in data:
        if 'FEIGN_TEST' in q.get('title', ''):
            s, _ = req(QUIZ_BASE, 'DELETE', f'/api/quizzes/{q["id"]}')
            print(f'[INFO] Cleaned up FEIGN_TEST quiz id={q["id"]} -> {s}')
