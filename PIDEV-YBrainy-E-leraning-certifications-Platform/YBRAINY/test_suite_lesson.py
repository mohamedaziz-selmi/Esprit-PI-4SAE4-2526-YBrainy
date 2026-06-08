import urllib.request, urllib.error, json, urllib.parse

COURSE_BASE = 'http://localhost:8082'
LESSON_BASE = 'http://localhost:8084'

def req_json(base, method, path, body=None):
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

def req_lesson_multipart(base, method, path, meta_dict, youtube_url=None, sequence_json=None):
    boundary = '----LessonBoundaryXyZ123'
    lesson_json = json.dumps(meta_dict).encode('utf-8')
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="lesson"; filename="lesson.json"\r\nContent-Type: application/json\r\n\r\n'.encode()
        + lesson_json + b'\r\n'
    )
    if sequence_json:
        body += (
            f'--{boundary}\r\nContent-Disposition: form-data; name="sequence"; filename="sequence.json"\r\nContent-Type: application/json\r\n\r\n'.encode()
            + sequence_json.encode('utf-8') + b'\r\n'
        )
    body += f'--{boundary}--\r\n'.encode()
    full_path = path
    if youtube_url:
        full_path = path + '?youtubeUrls=' + urllib.parse.quote(youtube_url)
    url = base + full_path
    headers = {'Content-Type': f'multipart/form-data; boundary={boundary}'}
    req_obj = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        res = urllib.request.urlopen(req_obj, timeout=10)
        raw = res.read()
        return res.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except: return e.code, {}
    except Exception as e:
        return 0, str(e)

print('=== SUITE 2: LESSON CRUD ===')

# Find a course with lessons
status, data = req_json(COURSE_BASE, 'GET', '/api/courses')
courses = data['content'] if isinstance(data, dict) else data
course_id = None
for c in courses:
    s, ld = req_json(LESSON_BASE, 'GET', f'/api/courses/{c["id"]}/lessons')
    if s == 200 and isinstance(ld, list) and len(ld) > 0:
        course_id = c['id']
        print(f'[INFO] Found course_id={course_id} with {len(ld)} lessons')
        break
if not course_id:
    course_id = courses[0]['id'] if courses else 125
    print(f'[INFO] No course with lessons, using course_id={course_id}')

# 2.1 Get lessons
status, data = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons')
if status == 200 and isinstance(data, list):
    print(f'[PASS] 2.1 GET /api/courses/{course_id}/lessons -> {len(data)} lessons')
    first_lesson = data[0] if data else None
    for l in data[:3]:
        print(f'       Lesson id={l["id"]} title="{l.get("title","")}" type={l.get("type","?")} order={l.get("orderIndex")}')
else:
    print(f'[FAIL] 2.1 GET lessons -> {status} {str(data)[:200]}')
    first_lesson = None

# 2.2 Single lesson with contents
if first_lesson:
    lid = first_lesson['id']
    status, data = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{lid}')
    if status == 200:
        contents = data.get('contents') or []
        print(f'[PASS] 2.2 GET lesson {lid} -> title="{data.get("title")}" type={data.get("type")} contents={len(contents)}')
        for c in contents:
            ctype = c.get('type', '?')
            curl = c.get('contentUrl', '')
            label = 'PASS' if curl else 'WARN'
            print(f'       [{label}] {ctype}: {curl[:70]}')
        if not contents:
            print(f'       [WARN] No contents on this lesson')
    else:
        print(f'[FAIL] 2.2 GET lesson -> {status}')

# 2.3 Create lesson with YouTube
meta = {
    'title': 'TEST_LESSON_DELETE_ME',
    'description': 'Automated test lesson',
    'orderIndex': 9999,
    'durationMinutes': 5
}
status, data = req_lesson_multipart(LESSON_BASE, 'POST', f'/api/courses/{course_id}/lessons',
    meta, youtube_url='https://www.youtube.com/watch?v=dQw4w9WgXcQ')
if status in [200, 201] and data.get('id'):
    test_lesson_id = data['id']
    contents = data.get('contents', [])
    print(f'[PASS] 2.3 POST create lesson -> id={test_lesson_id} contents={len(contents)}')
    for c in contents:
        print(f'       content: type={c.get("type")} url={c.get("contentUrl","")[:70]}')
else:
    print(f'[FAIL] 2.3 POST lesson -> {status} {str(data)[:300]}')
    test_lesson_id = None

# 2.4 Verify persisted
if test_lesson_id:
    status, data = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{test_lesson_id}')
    if status == 200 and data.get('title') == 'TEST_LESSON_DELETE_ME':
        contents = data.get('contents', [])
        print(f'[PASS] 2.4 Lesson persisted: title="{data.get("title")}" type={data.get("type")} contents={len(contents)}')
    else:
        print(f'[FAIL] 2.4 Lesson not persisted -> {status} title={data.get("title")}')

# 2.5 Update lesson preserving contents
if test_lesson_id:
    status, orig = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{test_lesson_id}')
    orig_contents = orig.get('contents', [])
    orig_count = len(orig_contents)

    update_meta = {
        'title': 'TEST_LESSON_UPDATED',
        'description': 'Updated description',
        'orderIndex': 9999,
        'durationMinutes': 10
    }
    sequence_json = json.dumps([
        {'existingContentId': c['id'], 'type': c.get('type', 'YOUTUBE_EMBED')}
        for c in orig_contents
    ])
    status, upd = req_lesson_multipart(LESSON_BASE, 'PUT',
        f'/api/courses/{course_id}/lessons/{test_lesson_id}',
        update_meta, sequence_json=sequence_json)
    if status in [200, 201]:
        status2, data2 = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{test_lesson_id}')
        after_count = len(data2.get('contents', []))
        if data2.get('title') == 'TEST_LESSON_UPDATED':
            if after_count >= orig_count:
                print(f'[PASS] 2.5 UPDATE lesson -> title updated, contents preserved: {orig_count} -> {after_count}')
            else:
                print(f'[FAIL] 2.5 UPDATE: content LOST! {orig_count} -> {after_count}')
        else:
            print(f'[FAIL] 2.5 Title not updated -> {data2.get("title")}')
    else:
        print(f'[FAIL] 2.5 PUT -> {status} {str(upd)[:200]}')

# 2.6 Content type audit across lessons
print('[INFO] 2.6 Auditing content types...')
status, lessons = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons')
if status == 200 and isinstance(lessons, list):
    all_types = {}
    for l in lessons[:5]:
        _, ld = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{l["id"]}')
        for c in ld.get('contents', []):
            t = c.get('type', '?')
            all_types[t] = all_types.get(t, 0) + 1
            curl = c.get('contentUrl', '')
            if curl:
                print(f'[PASS] 2.6 {t} has URL: {curl[:60]}')
            else:
                print(f'[FAIL] 2.6 {t} missing URL (lesson id={l["id"]})')
    if all_types:
        print(f'[INFO] 2.6 Type counts: {all_types}')
    else:
        print(f'[WARN] 2.6 No contents found in lessons')

# 2.7 Delete test lesson
if test_lesson_id:
    status, data = req_json(LESSON_BASE, 'DELETE', f'/api/courses/{course_id}/lessons/{test_lesson_id}')
    if status in [200, 204]:
        status2, _ = req_json(LESSON_BASE, 'GET', f'/api/courses/{course_id}/lessons/{test_lesson_id}')
        if status2 in [404, 400, 500]:
            print(f'[PASS] 2.7 DELETE lesson -> confirmed gone (GET -> {status2})')
        else:
            print(f'[FAIL] 2.7 Lesson still accessible after delete! GET -> {status2}')
    else:
        print(f'[FAIL] 2.7 DELETE -> {status} {str(data)[:200]}')

# 2.8 Lesson count via /api/lessons/course/{id}/count
status, data = req_json(LESSON_BASE, 'GET', f'/api/lessons/course/{course_id}/count')
if status == 200 and isinstance(data, int):
    print(f'[PASS] 2.8 Lesson count for course {course_id} -> {data}')
else:
    print(f'[FAIL] 2.8 Lesson count -> {status} {str(data)[:100]}')

# 2.9 Non-existent course
status, data = req_json(LESSON_BASE, 'GET', '/api/courses/999999/lessons')
if status == 200 and isinstance(data, list) and len(data) == 0:
    print(f'[PASS] 2.9 Non-existent course lessons -> empty list (graceful)')
elif status in [404, 400]:
    print(f'[PASS] 2.9 Non-existent course lessons -> {status}')
else:
    print(f'[INFO] 2.9 Non-existent course -> {status} count={len(data) if isinstance(data,list) else "N/A"}')

print(f'course_id={course_id}')
