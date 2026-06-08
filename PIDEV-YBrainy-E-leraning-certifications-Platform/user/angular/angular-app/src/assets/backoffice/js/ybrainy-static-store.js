/* YBrainy Backoffice - static localStorage data store (no API) */
(function () {
  const KEY = 'ybrainy.backoffice.static.v1';

  function now() {
    return Date.now();
  }

  function seed() {
    const t = now();
    const courses = [
      {
        id: t + 1,
        title: 'Cybersecurity Fundamentals',
        category: 'Cybersecurity',
        description: 'Understand threats, encryption, network security and ethical hacking basics.',
        thumbnailVideoPath: '',
        rating: 4.9,
        totalStudents: 980,
        createdAt: new Date().toISOString(),
      },
      {
        id: t + 2,
        title: 'UI/UX Design Essentials',
        category: 'Design',
        description: 'Learn user-centered design principles, wireframing, prototyping and usability testing.',
        thumbnailVideoPath: '',
        rating: 4.5,
        totalStudents: 415,
        createdAt: new Date().toISOString(),
      },
      {
        id: t + 3,
        title: 'Mobile App Development with Flutter',
        category: 'Mobile Development',
        description: 'Build beautiful, natively compiled mobile apps for iOS and Android from a single codebase.',
        thumbnailVideoPath: '',
        rating: 4.7,
        totalStudents: 632,
        createdAt: new Date().toISOString(),
      },
    ];

    const lessons = [
      {
        id: t + 101,
        courseId: courses[0].id,
        title: 'Security Basics',
        description: 'CIA triad, common attack vectors, and threat modeling.',
        duration: '22 min',
        videoPath: '',
        orderIndex: 0,
      },
      {
        id: t + 102,
        courseId: courses[0].id,
        title: 'Passwords & Hashing',
        description: 'Hashing vs encryption, salts, and best practices.',
        duration: '19 min',
        videoPath: '',
        orderIndex: 1,
      },
      {
        id: t + 201,
        courseId: courses[1].id,
        title: 'Design Foundations',
        description: 'Layout, typography, color systems and accessibility.',
        duration: '17 min',
        videoPath: '',
        orderIndex: 0,
      },
      {
        id: t + 301,
        courseId: courses[2].id,
        title: 'Dart Crash Course',
        description: 'Variables, functions, classes and null-safety.',
        duration: '20 min',
        videoPath: '',
        orderIndex: 0,
      },
    ];

    // Seed meets around current month for a nicer demo
    const base = new Date();
    base.setHours(10, 0, 0, 0);
    const y = base.getFullYear();
    const m = base.getMonth(); // 0-based

    function iso(d) {
      return d.toISOString();
    }

    const meets = [
      {
        id: t + 1001,
        title: 'Web Dev Q&A Session',
        meetLink: 'https://meet.google.com/',
        startTime: iso(new Date(y, m, 12, 10, 0, 0)),
        endTime: iso(new Date(y, m, 12, 11, 0, 0)),
        courseId: courses[2].id,
        color: 'bg-primary',
        description: 'Bring your questions.',
      },
      {
        id: t + 1002,
        title: 'Data Science Workshop',
        meetLink: 'https://meet.google.com/',
        startTime: iso(new Date(y, m, 13, 14, 30, 0)),
        endTime: iso(new Date(y, m, 13, 16, 0, 0)),
        courseId: courses[2].id,
        color: 'bg-success',
        description: 'Hands-on session.',
      },
      {
        id: t + 1003,
        title: 'Flutter UI Challenge',
        meetLink: 'https://meet.google.com/',
        startTime: iso(new Date(y, m, 14, 9, 0, 0)),
        endTime: iso(new Date(y, m, 14, 10, 0, 0)),
        courseId: courses[2].id,
        color: 'bg-warning',
        description: 'Build a small UI together.',
      },
      {
        id: t + 1004,
        title: 'Cybersecurity Live Lab',
        meetLink: 'https://meet.google.com/',
        startTime: iso(new Date(y, m, 16, 16, 0, 0)),
        endTime: iso(new Date(y, m, 16, 17, 0, 0)),
        courseId: courses[0].id,
        color: 'bg-danger',
        description: 'Threat modeling + demo.',
      },
      {
        id: t + 1005,
        title: 'General Instructor Office Hours',
        meetLink: 'https://meet.google.com/',
        startTime: iso(new Date(y, m, 18, 11, 0, 0)),
        endTime: iso(new Date(y, m, 18, 12, 0, 0)),
        courseId: '',
        color: 'bg-info',
        description: 'Open questions.',
      },
    ].map((mt) => {
      const cid = mt.courseId ? Number(mt.courseId) : null;
      const c = cid ? courses.find((x) => Number(x.id) === cid) : null;
      return { ...mt, courseTitle: c ? c.title : '' };
    });

    return { courses, lessons, meets };
  }

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return seed();
      const parsed = JSON.parse(raw);
      if (
        !parsed ||
        !Array.isArray(parsed.courses) ||
        !Array.isArray(parsed.lessons) ||
        !Array.isArray(parsed.meets)
      )
        return seed();
      return parsed;
    } catch {
      return seed();
    }
  }

  function save(state) {
    localStorage.setItem(KEY, JSON.stringify(state));
  }

  function nextId(state) {
    const ids = []
      .concat(state.courses.map((c) => c.id))
      .concat(state.lessons.map((l) => l.id));
    const max = ids.length ? Math.max.apply(null, ids) : now();
    return max + 1;
  }

  function byCreatedDesc(a, b) {
    const ta = a.createdAt ? Date.parse(a.createdAt) : 0;
    const tb = b.createdAt ? Date.parse(b.createdAt) : 0;
    return tb - ta;
  }

  const api = {
    reset: function () {
      const s = seed();
      save(s);
      return s;
    },

    getCourses: function () {
      const s = load();
      return s.courses.slice().sort(byCreatedDesc);
    },

    createCourse: function (input) {
      const s = load();
      const id = nextId(s);
      const course = {
        id,
        title: (input.title || '').trim(),
        category: (input.category || '').trim(),
        description: (input.description || '').trim(),
        thumbnailVideoPath: (input.thumbnailVideoPath || '').trim(),
        rating: typeof input.rating === 'number' ? input.rating : Number(input.rating || 0),
        totalStudents: typeof input.totalStudents === 'number' ? input.totalStudents : Number(input.totalStudents || 0),
        createdAt: new Date().toISOString(),
      };
      s.courses.unshift(course);
      save(s);
      return course;
    },

    deleteCourse: function (courseId) {
      const s = load();
      const id = Number(courseId);
      s.courses = s.courses.filter((c) => Number(c.id) !== id);
      s.lessons = s.lessons.filter((l) => Number(l.courseId) !== id);
      s.meets = s.meets.filter((m) => Number(m.courseId || 0) !== id);
      save(s);
    },

    getLessonsByCourse: function (courseId) {
      const s = load();
      const id = Number(courseId);
      return s.lessons
        .filter((l) => Number(l.courseId) === id)
        .slice()
        .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    },

    createLesson: function (courseId, input) {
      const s = load();
      const id = nextId(s);
      const cid = Number(courseId);
      const existing = s.lessons.filter((l) => Number(l.courseId) === cid);
      const maxOrder = existing.length ? Math.max.apply(null, existing.map((l) => l.orderIndex ?? 0)) : -1;
      const lesson = {
        id,
        courseId: cid,
        title: (input.title || '').trim(),
        description: (input.description || '').trim(),
        duration: (input.duration || '').trim(),
        videoPath: (input.videoPath || '').trim(),
        orderIndex: maxOrder + 1,
      };
      s.lessons.push(lesson);
      save(s);
      return lesson;
    },

    deleteLesson: function (lessonId) {
      const s = load();
      const id = Number(lessonId);
      s.lessons = s.lessons.filter((l) => Number(l.id) !== id);
      save(s);
    },

    reorderLessons: function (courseId, orderedLessonIds) {
      const s = load();
      const cid = Number(courseId);
      const order = (orderedLessonIds || []).map((x) => Number(x));
      const idx = new Map(order.map((id, i) => [id, i]));
      s.lessons = s.lessons.map((l) => {
        if (Number(l.courseId) !== cid) return l;
        const i = idx.get(Number(l.id));
        if (typeof i !== 'number') return l;
        return { ...l, orderIndex: i };
      });
      save(s);
    },

    // ----- Meets (Calendar) -----
    getMeets: function () {
      const s = load();
      return s.meets.slice().sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
    },

    createMeet: function (input) {
      const s = load();
      const id = nextId(s);
      const cid = input.courseId ? Number(input.courseId) : null;
      const c = cid ? s.courses.find((x) => Number(x.id) === cid) : null;
      const meet = {
        id,
        title: (input.title || '').trim(),
        meetLink: (input.meetLink || '').trim(),
        startTime: (input.startTime || '').trim(),
        endTime: (input.endTime || '').trim(),
        description: (input.description || '').trim(),
        color: (input.color || 'bg-primary').trim(),
        courseId: cid ? cid : '',
        courseTitle: c ? c.title : '',
      };
      s.meets.push(meet);
      save(s);
      return meet;
    },

    updateMeet: function (meetId, patch) {
      const s = load();
      const id = Number(meetId);
      s.meets = s.meets.map((m) => {
        if (Number(m.id) !== id) return m;
        const next = { ...m, ...patch };
        // keep courseTitle in sync
        const cid = next.courseId ? Number(next.courseId) : null;
        const c = cid ? s.courses.find((x) => Number(x.id) === cid) : null;
        next.courseTitle = c ? c.title : '';
        return next;
      });
      save(s);
    },

    deleteMeet: function (meetId) {
      const s = load();
      const id = Number(meetId);
      s.meets = s.meets.filter((m) => Number(m.id) !== id);
      save(s);
    },
  };

  window.YBrainyStore = api;
})();


