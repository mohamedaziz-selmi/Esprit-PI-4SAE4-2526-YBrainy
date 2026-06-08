"""
YBrainy Database Seeding Script
Seeds 80 courses with 4 lessons each, 1 quiz with 5 questions per course,
enrollments for 2 students, and reviews.
"""

import requests
import json
import time
import sys

COURSE_BASE = "http://localhost:8082"
QUIZ_BASE   = "http://localhost:8083"

YOUTUBE_URLS = [
    "https://www.youtube.com/watch?v=PkZNo7MFNFg",
    "https://www.youtube.com/watch?v=rfscVS0vtbw",
    "https://www.youtube.com/watch?v=_uQrJ0TkZlc",
    "https://www.youtube.com/watch?v=8JJ101D3knE",
]

# ── Course definitions ─────────────────────────────────────────────────────────

COURSES = [
    # PROGRAMMING (20)
    {
        "title": "JavaScript for Beginners",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Start your web development journey with JavaScript, the language of the web. Learn variables, functions, arrays, and DOM manipulation through hands-on projects. By the end you will build interactive web pages from scratch.",
        "topic": "JavaScript",
        "questions": [
            {"q": "What keyword declares a constant in JavaScript?", "options": ["var", "let", "const", "def"], "correct": 2},
            {"q": "Which method adds an element to the end of an array?", "options": ["push()", "pop()", "shift()", "splice()"], "correct": 0},
            {"q": "What does the === operator check?", "options": ["Assignment", "Value only", "Value and type", "Reference"], "correct": 2},
            {"q": "Which of the following is a JavaScript framework?", "options": ["Django", "Laravel", "React", "Flask"], "correct": 2},
            {"q": "How do you write a comment in JavaScript?", "options": ["# comment", "<!-- comment -->", "// comment", "** comment"], "correct": 2},
        ],
    },
    {
        "title": "Python from Scratch",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Discover Python, one of the most versatile and beginner-friendly programming languages. This course covers core syntax, data types, loops, and functions with real-world examples. Perfect for complete beginners with no coding experience.",
        "topic": "Python",
        "questions": [
            {"q": "What is the correct way to define a function in Python?", "options": ["function foo():", "def foo():", "func foo():", "define foo():"], "correct": 1},
            {"q": "Which data type is mutable in Python?", "options": ["tuple", "string", "list", "int"], "correct": 2},
            {"q": "What does len() return for the string 'hello'?", "options": ["4", "5", "6", "hello"], "correct": 1},
            {"q": "How do you start a for loop in Python?", "options": ["for i in range(5):", "for (i=0;i<5;i++)", "foreach i in range(5):", "loop i to 5:"], "correct": 0},
            {"q": "Which symbol is used for single-line comments in Python?", "options": ["//", "/*", "#", "--"], "correct": 2},
        ],
    },
    {
        "title": "Python Data Analysis",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": True,
        "description": "Master data analysis using Python's powerful libraries including Pandas, NumPy, and Matplotlib. Learn to clean, transform, and visualize datasets to uncover meaningful insights. Ideal for aspiring data analysts and scientists.",
        "topic": "Data Analysis with Python",
        "questions": [
            {"q": "Which Pandas method is used to read a CSV file?", "options": ["pd.load_csv()", "pd.read_csv()", "pd.open_csv()", "pd.import_csv()"], "correct": 1},
            {"q": "What does df.head() return by default?", "options": ["Last 5 rows", "First 5 rows", "First row", "Column names"], "correct": 1},
            {"q": "Which NumPy function creates an array of zeros?", "options": ["np.empty()", "np.zeros()", "np.blank()", "np.null()"], "correct": 1},
            {"q": "How do you select a column named 'age' from a DataFrame df?", "options": ["df.column['age']", "df.get('age')", "df['age']", "df.select('age')"], "correct": 2},
            {"q": "What does df.describe() provide?", "options": ["Column names", "Data types", "Summary statistics", "Row count only"], "correct": 2},
        ],
    },
    {
        "title": "CSS & HTML Mastery",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Build beautiful, responsive websites by mastering HTML5 and CSS3 from the ground up. Learn semantic markup, Flexbox, Grid, and modern CSS animations. This course gives you everything you need to create professional web pages.",
        "topic": "CSS and HTML",
        "questions": [
            {"q": "Which HTML tag is used for the largest heading?", "options": ["<h6>", "<h1>", "<header>", "<title>"], "correct": 1},
            {"q": "What does CSS stand for?", "options": ["Creative Style Sheets", "Cascading Style Sheets", "Computer Style Syntax", "Custom Styling Service"], "correct": 1},
            {"q": "Which CSS property controls text color?", "options": ["font-color", "text-color", "color", "foreground"], "correct": 2},
            {"q": "How do you make a CSS class selector?", "options": ["#myClass", ".myClass", "*myClass", "&myClass"], "correct": 1},
            {"q": "Which HTML element defines a hyperlink?", "options": ["<link>", "<href>", "<a>", "<nav>"], "correct": 2},
        ],
    },
    {
        "title": "SQL Database Fundamentals",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Learn to design, query, and manage relational databases using SQL. This course covers SELECT, JOIN, GROUP BY, subqueries, and database normalization with practical exercises. A must-have skill for developers and data professionals.",
        "topic": "SQL",
        "questions": [
            {"q": "Which SQL clause filters rows after grouping?", "options": ["WHERE", "HAVING", "FILTER", "SELECT"], "correct": 1},
            {"q": "What does SELECT DISTINCT do?", "options": ["Selects all rows", "Removes duplicate rows", "Sorts results", "Joins tables"], "correct": 1},
            {"q": "Which JOIN returns all rows from both tables?", "options": ["INNER JOIN", "LEFT JOIN", "FULL OUTER JOIN", "CROSS JOIN"], "correct": 2},
            {"q": "What is a PRIMARY KEY?", "options": ["A duplicate column", "A unique identifier for each row", "A foreign reference", "An index column"], "correct": 1},
            {"q": "Which statement removes all rows from a table without deleting it?", "options": ["DELETE FROM table", "DROP TABLE", "TRUNCATE TABLE", "REMOVE TABLE"], "correct": 2},
        ],
    },
    {
        "title": "Docker & Containerization",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": True,
        "description": "Master Docker to package, ship, and run applications in isolated containers. Learn Dockerfiles, images, networks, volumes, and Docker Compose for multi-container apps. This course is essential for modern DevOps workflows.",
        "topic": "Docker",
        "questions": [
            {"q": "What command builds a Docker image?", "options": ["docker run", "docker build", "docker create", "docker start"], "correct": 1},
            {"q": "What is a Dockerfile?", "options": ["A runtime log", "A script to build an image", "A container registry", "A compose config"], "correct": 1},
            {"q": "Which command lists running containers?", "options": ["docker list", "docker ps", "docker containers", "docker show"], "correct": 1},
            {"q": "What does docker-compose up do?", "options": ["Stops all services", "Starts all services defined in compose file", "Builds images only", "Removes volumes"], "correct": 1},
            {"q": "What is a Docker volume used for?", "options": ["Network configuration", "Persisting data outside containers", "Building images", "Port mapping"], "correct": 1},
        ],
    },
    {
        "title": "Data Science with Python",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": True,
        "description": "Explore the complete data science workflow from data collection to model deployment using Python. Covers statistics, machine learning basics, feature engineering, and visualization with Seaborn. Build real data science projects to enhance your portfolio.",
        "topic": "Data Science",
        "questions": [
            {"q": "What is the purpose of train-test split?", "options": ["Increase data size", "Evaluate model on unseen data", "Clean missing values", "Normalize features"], "correct": 1},
            {"q": "Which metric measures classification accuracy?", "options": ["RMSE", "R-squared", "F1-score", "MAE"], "correct": 2},
            {"q": "What does feature scaling do?", "options": ["Removes outliers", "Normalizes feature ranges", "Encodes categories", "Selects features"], "correct": 1},
            {"q": "Which library provides the train_test_split function?", "options": ["pandas", "numpy", "sklearn", "matplotlib"], "correct": 2},
            {"q": "What is overfitting?", "options": ["Model performs poorly on training data", "Model memorizes training data, fails on new data", "Model is too simple", "Model has too few parameters"], "correct": 1},
        ],
    },
    {
        "title": "React.js Complete Guide",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Build modern single-page applications using React.js with hooks, context API, and routing. Learn component-based architecture, state management, and REST API integration. Create production-ready apps with best practices.",
        "topic": "React.js",
        "questions": [
            {"q": "What hook manages state in functional components?", "options": ["useEffect", "useState", "useContext", "useRef"], "correct": 1},
            {"q": "What is JSX?", "options": ["A CSS framework", "JavaScript XML syntax extension", "A React testing tool", "A build tool"], "correct": 1},
            {"q": "Which hook runs side effects in React?", "options": ["useState", "useMemo", "useEffect", "useCallback"], "correct": 2},
            {"q": "What does props stand for in React?", "options": ["Properties", "Prototypes", "Procedures", "Processes"], "correct": 0},
            {"q": "How do you pass data from parent to child in React?", "options": ["Via state", "Via props", "Via context only", "Via events only"], "correct": 1},
        ],
    },
    {
        "title": "Node.js Backend Development",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": False,
        "description": "Build scalable server-side applications with Node.js and Express. Learn routing, middleware, authentication, database integration with MongoDB, and REST API design. This course takes you from zero to production-ready backend developer.",
        "topic": "Node.js",
        "questions": [
            {"q": "What is Node.js?", "options": ["A frontend framework", "A server-side JavaScript runtime", "A database", "A CSS preprocessor"], "correct": 1},
            {"q": "Which function starts an Express server?", "options": ["app.start()", "app.listen()", "app.run()", "app.serve()"], "correct": 1},
            {"q": "What is middleware in Express?", "options": ["A database layer", "Functions that process requests", "A frontend library", "A routing file"], "correct": 1},
            {"q": "Which module handles HTTP requests in Node.js?", "options": ["fs", "path", "http", "os"], "correct": 2},
            {"q": "What does npm stand for?", "options": ["Node Package Manager", "New Programming Module", "Network Process Manager", "Node Process Module"], "correct": 0},
        ],
    },
    {
        "title": "Java Spring Boot",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 39.99,
        "offersCertificate": True,
        "description": "Develop enterprise-grade REST APIs and web applications using Spring Boot and Spring MVC. Learn dependency injection, JPA with Hibernate, security, and testing. This course is your gateway to professional Java backend development.",
        "topic": "Spring Boot",
        "questions": [
            {"q": "What annotation marks a Spring REST controller?", "options": ["@Controller", "@Service", "@RestController", "@Repository"], "correct": 2},
            {"q": "What does @Autowired do?", "options": ["Defines a bean", "Injects a dependency automatically", "Marks a REST endpoint", "Creates a table"], "correct": 1},
            {"q": "Which annotation maps HTTP GET requests?", "options": ["@PostMapping", "@GetMapping", "@PutMapping", "@RequestBody"], "correct": 1},
            {"q": "What is Spring Data JPA used for?", "options": ["Frontend templating", "Database access and ORM", "Security configuration", "Testing"], "correct": 1},
            {"q": "What file configures Spring Boot properties?", "options": ["web.xml", "pom.xml", "application.properties", "beans.xml"], "correct": 2},
        ],
    },
    {
        "title": "Machine Learning Fundamentals",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 44.99,
        "offersCertificate": True,
        "description": "Understand and implement core machine learning algorithms including regression, classification, clustering, and ensemble methods. Gain hands-on experience with scikit-learn on real datasets. Develop intuition for choosing the right algorithm.",
        "topic": "Machine Learning",
        "questions": [
            {"q": "What type of learning uses labeled data?", "options": ["Unsupervised learning", "Reinforcement learning", "Supervised learning", "Semi-supervised learning"], "correct": 2},
            {"q": "What does a decision tree split on?", "options": ["Random features", "The feature that best reduces impurity", "The most correlated feature", "The last feature"], "correct": 1},
            {"q": "Which algorithm is used for clustering?", "options": ["Linear Regression", "K-Means", "Logistic Regression", "SVM"], "correct": 1},
            {"q": "What is cross-validation used for?", "options": ["Data cleaning", "Feature selection", "Model evaluation and tuning", "Data augmentation"], "correct": 2},
            {"q": "What does a confusion matrix show?", "options": ["Feature correlations", "Correct and incorrect predictions", "Training loss", "Model parameters"], "correct": 1},
        ],
    },
    {
        "title": "Deep Learning with TensorFlow",
        "category": "PROGRAMMING",
        "level": "ADVANCED",
        "price": 59.99,
        "offersCertificate": True,
        "description": "Build and train deep neural networks using TensorFlow 2 and Keras for image recognition, NLP, and time series prediction. Covers CNNs, RNNs, transfer learning, and model optimization techniques. Ideal for ML practitioners ready for the next level.",
        "topic": "Deep Learning",
        "questions": [
            {"q": "What is the role of an activation function?", "options": ["Initialize weights", "Introduce non-linearity", "Reduce learning rate", "Normalize inputs"], "correct": 1},
            {"q": "Which layer type is used in CNNs?", "options": ["Dense", "LSTM", "Convolutional", "Dropout"], "correct": 2},
            {"q": "What does backpropagation compute?", "options": ["Forward pass predictions", "Gradients of the loss function", "Batch normalization", "Layer outputs"], "correct": 1},
            {"q": "What is transfer learning?", "options": ["Moving data between servers", "Using pre-trained models on new tasks", "Training from random weights", "Compressing a model"], "correct": 1},
            {"q": "Which optimizer adaptively adjusts learning rates?", "options": ["SGD", "Momentum", "Adam", "RMSprop only"], "correct": 2},
        ],
    },
    {
        "title": "Git & Version Control",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Master Git for tracking code changes, collaborating with teams, and managing project history. Learn branching strategies, merging, rebasing, and working with GitHub. Essential knowledge for every software developer.",
        "topic": "Git",
        "questions": [
            {"q": "What command initializes a new Git repository?", "options": ["git start", "git init", "git create", "git new"], "correct": 1},
            {"q": "What does git commit do?", "options": ["Uploads code to GitHub", "Saves staged changes to local history", "Merges branches", "Clones a repo"], "correct": 1},
            {"q": "What is a Git branch?", "options": ["A backup copy", "An independent line of development", "A remote server", "A merge conflict"], "correct": 1},
            {"q": "What does git pull do?", "options": ["Pushes local changes", "Fetches and merges remote changes", "Creates a branch", "Stashes changes"], "correct": 1},
            {"q": "Which command stages all changed files?", "options": ["git commit -a", "git add .", "git push all", "git stage"], "correct": 1},
        ],
    },
    {
        "title": "REST API Design",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Learn principles and best practices for designing clean, scalable REST APIs. Topics include HTTP methods, status codes, versioning, pagination, authentication, and OpenAPI documentation. Build APIs that developers love to use.",
        "topic": "REST API Design",
        "questions": [
            {"q": "Which HTTP method is idempotent and retrieves data?", "options": ["POST", "DELETE", "GET", "PATCH"], "correct": 2},
            {"q": "What HTTP status code indicates a resource was created?", "options": ["200", "201", "204", "400"], "correct": 1},
            {"q": "What does REST stand for?", "options": ["Remote Execution Service Transfer", "Representational State Transfer", "Request-Endpoint-Service-Token", "Real-time Event Streaming Transport"], "correct": 1},
            {"q": "Which HTTP method is used for partial updates?", "options": ["PUT", "POST", "PATCH", "GET"], "correct": 2},
            {"q": "What does a 404 status code mean?", "options": ["Server error", "Unauthorized", "Resource not found", "Bad request"], "correct": 2},
        ],
    },
    {
        "title": "Cybersecurity Fundamentals",
        "category": "PROGRAMMING",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Build a solid foundation in cybersecurity concepts including threats, vulnerabilities, encryption, and network security. Learn about common attack vectors like phishing, SQL injection, and XSS. Ideal for anyone starting a career in security.",
        "topic": "Cybersecurity",
        "questions": [
            {"q": "What is a SQL injection attack?", "options": ["Injecting viruses via email", "Inserting malicious SQL into input fields", "Stealing session cookies", "Brute forcing passwords"], "correct": 1},
            {"q": "What does HTTPS protect against?", "options": ["Viruses", "Man-in-the-middle attacks", "SQL injection", "DDoS attacks"], "correct": 1},
            {"q": "What is phishing?", "options": ["Network scanning", "Trick users into revealing credentials", "Encrypting files for ransom", "Denial of service"], "correct": 1},
            {"q": "What does a firewall do?", "options": ["Encrypts data", "Scans for viruses", "Controls network traffic", "Manages passwords"], "correct": 2},
            {"q": "What is two-factor authentication?", "options": ["Two passwords", "Password plus a second verification method", "Two usernames", "Biometric only"], "correct": 1},
        ],
    },
    {
        "title": "TypeScript Mastery",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": True,
        "description": "Elevate your JavaScript code with TypeScript's static typing, interfaces, and advanced type system. Learn generics, decorators, type inference, and how to migrate JavaScript projects to TypeScript. Essential for large-scale frontend development.",
        "topic": "TypeScript",
        "questions": [
            {"q": "What is the main benefit of TypeScript over JavaScript?", "options": ["Runs faster", "Static type checking", "No need for a build step", "Better UI components"], "correct": 1},
            {"q": "What keyword defines a TypeScript interface?", "options": ["type", "class", "interface", "struct"], "correct": 2},
            {"q": "What is a generic in TypeScript?", "options": ["A catch-all variable", "A type placeholder for reusable code", "An async function", "A module pattern"], "correct": 1},
            {"q": "Which TypeScript type means a value can be null or undefined?", "options": ["any", "void", "never", "optional"], "correct": 0},
            {"q": "How do you compile TypeScript to JavaScript?", "options": ["ts compile", "tsc", "babel ts", "node ts"], "correct": 1},
        ],
    },
    {
        "title": "Algorithms & Data Structures",
        "category": "PROGRAMMING",
        "level": "ADVANCED",
        "price": 49.99,
        "offersCertificate": True,
        "description": "Master essential algorithms and data structures for technical interviews and efficient software engineering. Covers arrays, linked lists, trees, graphs, sorting, searching, and dynamic programming. Solve complex problems with elegant, optimized solutions.",
        "topic": "Algorithms and Data Structures",
        "questions": [
            {"q": "What is the time complexity of binary search?", "options": ["O(n)", "O(n log n)", "O(log n)", "O(1)"], "correct": 2},
            {"q": "Which data structure uses LIFO order?", "options": ["Queue", "Stack", "Heap", "Graph"], "correct": 1},
            {"q": "What does BFS stand for?", "options": ["Binary File Search", "Breadth-First Search", "Best-First Scan", "Block Fragment Sort"], "correct": 1},
            {"q": "What is the worst-case time complexity of quicksort?", "options": ["O(n log n)", "O(n)", "O(n²)", "O(log n)"], "correct": 2},
            {"q": "What is dynamic programming?", "options": ["Typing code dynamically", "Solving problems by breaking into overlapping subproblems", "Runtime code generation", "Parallel execution"], "correct": 1},
        ],
    },
    {
        "title": "Cloud Computing AWS",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 54.99,
        "offersCertificate": True,
        "description": "Get hands-on with Amazon Web Services core services including EC2, S3, RDS, Lambda, and IAM. Learn to architect, deploy, and scale cloud-native applications with AWS best practices. Prepare for AWS Solutions Architect certification.",
        "topic": "AWS Cloud Computing",
        "questions": [
            {"q": "What is Amazon S3 used for?", "options": ["Compute capacity", "Object storage", "DNS management", "Email service"], "correct": 1},
            {"q": "What does EC2 stand for?", "options": ["Elastic Cloud Cluster", "Elastic Compute Cloud", "External Cloud Container", "Enhanced Cloud Core"], "correct": 1},
            {"q": "What is AWS Lambda?", "options": ["A container service", "A serverless compute service", "A database service", "A load balancer"], "correct": 1},
            {"q": "What does IAM stand for?", "options": ["Internet Access Management", "Identity and Access Management", "Infrastructure Automation Module", "Integrated API Manager"], "correct": 1},
            {"q": "Which AWS service provides a managed relational database?", "options": ["DynamoDB", "S3", "RDS", "Redshift"], "correct": 2},
        ],
    },
    {
        "title": "DevOps CI/CD Pipelines",
        "category": "PROGRAMMING",
        "level": "ADVANCED",
        "price": 64.99,
        "offersCertificate": True,
        "description": "Build automated CI/CD pipelines using Jenkins, GitHub Actions, and GitLab CI to streamline software delivery. Learn testing automation, containerized deployments, blue-green strategies, and infrastructure as code. Transform your team's delivery velocity.",
        "topic": "DevOps and CI/CD",
        "questions": [
            {"q": "What does CI/CD stand for?", "options": ["Code Integration/Code Delivery", "Continuous Integration/Continuous Delivery", "Compute Instance/Cloud Deploy", "Config Integration/Config Deploy"], "correct": 1},
            {"q": "What is the purpose of a CI pipeline?", "options": ["Manual code reviews", "Automated build and test on code changes", "User acceptance testing", "Infrastructure provisioning only"], "correct": 1},
            {"q": "What is Infrastructure as Code (IaC)?", "options": ["Writing business logic", "Managing infrastructure through configuration files", "Documenting APIs", "Monitoring logs"], "correct": 1},
            {"q": "Which tool is commonly used for CI/CD?", "options": ["Photoshop", "Jenkins", "Excel", "Slack"], "correct": 1},
            {"q": "What is a blue-green deployment?", "options": ["Color-coded code reviews", "Two identical environments for zero-downtime releases", "Branch naming convention", "Testing on production"], "correct": 1},
        ],
    },
    {
        "title": "Mobile App with Flutter",
        "category": "PROGRAMMING",
        "level": "INTERMEDIATE",
        "price": 39.99,
        "offersCertificate": False,
        "description": "Build beautiful cross-platform mobile apps for iOS and Android using Flutter and Dart. Learn widgets, navigation, state management with Provider, and API integration. Ship a single codebase to multiple platforms with native performance.",
        "topic": "Flutter",
        "questions": [
            {"q": "What language does Flutter use?", "options": ["Java", "Kotlin", "Dart", "Swift"], "correct": 2},
            {"q": "What is a Widget in Flutter?", "options": ["A database model", "A UI building block", "A network call", "A test fixture"], "correct": 1},
            {"q": "Which widget manages mutable state?", "options": ["StatelessWidget", "StatefulWidget", "Container", "Scaffold"], "correct": 1},
            {"q": "What does hot reload do in Flutter?", "options": ["Restarts the device", "Updates UI without losing state", "Recompiles from scratch", "Clears cache"], "correct": 1},
            {"q": "Which Flutter package is used for state management?", "options": ["http", "provider", "path", "dio"], "correct": 1},
        ],
    },

    # DESIGN (10)
    {
        "title": "UI/UX Design Principles",
        "category": "DESIGN",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Learn the foundational principles of user interface and user experience design including layout, typography, color theory, and usability heuristics. Understand how to design products that are both beautiful and functional. Build a design-thinking mindset.",
        "topic": "UI/UX Design",
        "questions": [
            {"q": "What does UX stand for?", "options": ["Universal eXchange", "User eXperience", "Uniform eXecution", "Unique eXpression"], "correct": 1},
            {"q": "What is the primary goal of UX design?", "options": ["Making things look pretty", "Solving user problems effectively", "Writing code", "Marketing products"], "correct": 1},
            {"q": "What is a wireframe?", "options": ["A high-fidelity mockup", "A low-fidelity layout sketch", "A finished design", "A user interview"], "correct": 1},
            {"q": "What does affordance mean in design?", "options": ["Cost of a product", "Visual cue of how something works", "Accessibility feature", "Color scheme"], "correct": 1},
            {"q": "Which principle says important elements should stand out?", "options": ["Proximity", "Alignment", "Visual hierarchy", "Repetition"], "correct": 2},
        ],
    },
    {
        "title": "Figma for Beginners",
        "category": "DESIGN",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Get started with Figma, the industry-standard design tool for creating UI mockups, prototypes, and design systems. Learn frames, components, auto-layout, and collaborative workflows. Design professional interfaces without any prior experience.",
        "topic": "Figma",
        "questions": [
            {"q": "What is Figma primarily used for?", "options": ["Video editing", "3D modeling", "UI/UX design and prototyping", "Code compilation"], "correct": 2},
            {"q": "What is a component in Figma?", "options": ["A page layout", "A reusable design element", "A color palette", "An export format"], "correct": 1},
            {"q": "What does auto-layout do in Figma?", "options": ["Exports assets", "Automatically arranges and resizes elements", "Applies color styles", "Imports images"], "correct": 1},
            {"q": "How do you create a prototype link in Figma?", "options": ["Draw an arrow", "Use the Prototype tab to connect frames", "Use the Assets panel", "Export to HTML"], "correct": 1},
            {"q": "What is a design system?", "options": ["A set of fonts", "A collection of reusable components and guidelines", "A grid template", "An animation library"], "correct": 1},
        ],
    },
    {
        "title": "Adobe Photoshop Mastery",
        "category": "DESIGN",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": True,
        "description": "Develop professional photo editing and digital art skills using Adobe Photoshop. Learn layers, masks, selections, retouching, compositing, and advanced color grading techniques. Create stunning visuals for web, print, and social media.",
        "topic": "Photoshop",
        "questions": [
            {"q": "What is a layer mask used for?", "options": ["Merging all layers", "Hiding parts of a layer non-destructively", "Locking a layer", "Flattening the image"], "correct": 1},
            {"q": "What does the Clone Stamp tool do?", "options": ["Applies color", "Copies pixels from one area to another", "Selects objects", "Crops the image"], "correct": 1},
            {"q": "What is the shortcut to deselect in Photoshop?", "options": ["Ctrl+Z", "Ctrl+D", "Ctrl+S", "Ctrl+A"], "correct": 1},
            {"q": "What is a Smart Object?", "options": ["An animated layer", "A layer that preserves original data for non-destructive edits", "A vector shape", "A merged layer group"], "correct": 1},
            {"q": "Which tool removes backgrounds in Photoshop?", "options": ["Crop tool", "Background Eraser tool", "Pencil tool", "Brush tool"], "correct": 1},
        ],
    },
    {
        "title": "Video Editing with Premiere",
        "category": "DESIGN",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": False,
        "description": "Learn professional video editing with Adobe Premiere Pro including cutting, transitions, color grading, audio mixing, and motion graphics. Produce high-quality videos for YouTube, social media, and film. Master the industry-standard video editing workflow.",
        "topic": "Video Editing",
        "questions": [
            {"q": "What is a timeline in Premiere Pro?", "options": ["A project folder", "Where video clips are arranged for editing", "A color wheel", "An audio waveform"], "correct": 1},
            {"q": "What does J-cut mean in video editing?", "options": ["Jump cut", "Audio from next scene starts before video transition", "Joint transition", "Jump to next clip"], "correct": 1},
            {"q": "What is color grading?", "options": ["Sorting clips by color", "Adjusting the look and mood of footage", "Adding captions", "Exporting video"], "correct": 1},
            {"q": "What is a B-roll?", "options": ["The main interview footage", "Supplementary footage that supports the main story", "An audio track", "A title sequence"], "correct": 1},
            {"q": "What format is best for final video export?", "options": ["BMP", "H.264/MP4", "RAW", "GIF"], "correct": 1},
        ],
    },
    {
        "title": "3D Modeling with Blender",
        "category": "DESIGN",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Create stunning 3D models, animations, and renders using Blender, the powerful open-source 3D software. Learn mesh editing, materials, lighting, rigging, and rendering with Cycles and EEVEE. From beginner to confident 3D artist.",
        "topic": "3D Modeling",
        "questions": [
            {"q": "What is a mesh in 3D modeling?", "options": ["A texture file", "A collection of vertices, edges, and faces", "A lighting setup", "A render setting"], "correct": 1},
            {"q": "What mode is used to edit vertices in Blender?", "options": ["Object Mode", "Sculpt Mode", "Edit Mode", "Pose Mode"], "correct": 2},
            {"q": "What is UV unwrapping?", "options": ["Applying a material", "Flattening a 3D surface to apply a 2D texture", "Rigging a character", "Setting up a camera"], "correct": 1},
            {"q": "What is a normal map?", "options": ["A standard texture", "A map that fakes surface detail without extra geometry", "A color grading preset", "A lighting map"], "correct": 1},
            {"q": "What render engine produces photorealistic results in Blender?", "options": ["EEVEE", "Workbench", "Cycles", "OpenGL"], "correct": 2},
        ],
    },
    {
        "title": "Graphic Design Fundamentals",
        "category": "DESIGN",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Build a strong foundation in graphic design principles including typography, color theory, grid systems, and visual communication. Learn to create logos, flyers, and social media graphics. Develop an eye for design that will serve you in any creative role.",
        "topic": "Graphic Design",
        "questions": [
            {"q": "What are the three primary colors in the RGB model?", "options": ["Red, Green, Blue", "Red, Yellow, Blue", "Cyan, Magenta, Yellow", "Orange, Green, Violet"], "correct": 0},
            {"q": "What is kerning?", "options": ["Line spacing", "Adjusting space between specific characters", "Font size", "Text alignment"], "correct": 1},
            {"q": "What is a sans-serif font?", "options": ["A font with small strokes at letter ends", "A font without decorative strokes", "A handwritten font", "A monospace font"], "correct": 1},
            {"q": "What is the golden ratio used for?", "options": ["Color mixing", "Creating harmonious proportions in design", "Grid alignment only", "Typography sizing"], "correct": 1},
            {"q": "What is the rule of thirds?", "options": ["Using three colors max", "Dividing composition into thirds for visual balance", "Three typeface rule", "Three column layout"], "correct": 1},
        ],
    },
    {
        "title": "Motion Graphics with After Effects",
        "category": "DESIGN",
        "level": "ADVANCED",
        "price": 44.99,
        "offersCertificate": True,
        "description": "Create professional motion graphics, animations, and visual effects using Adobe After Effects. Learn keyframing, expressions, masks, 3D layers, and particle systems. Produce broadcast-quality animations for video, web, and advertising.",
        "topic": "Motion Graphics",
        "questions": [
            {"q": "What is a keyframe in animation?", "options": ["A video frame", "A marker that defines a property's value at a specific time", "A composition setting", "A render preset"], "correct": 1},
            {"q": "What is an expression in After Effects?", "options": ["A comment", "JavaScript code that controls layer properties", "A color effect", "A text animation"], "correct": 1},
            {"q": "What does easing do in animation?", "options": ["Speeds up render", "Controls the acceleration and deceleration of movement", "Adjusts frame rate", "Loops animation"], "correct": 1},
            {"q": "What is a pre-composition?", "options": ["A template", "Grouping layers into a nested composition", "A render queue", "A motion preset"], "correct": 1},
            {"q": "What is parenting in After Effects?", "options": ["Grouping text layers", "Linking a child layer to follow a parent layer's movement", "Nesting compositions", "Duplicating layers"], "correct": 1},
        ],
    },
    {
        "title": "Web Design with Tailwind",
        "category": "DESIGN",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Design modern, responsive websites quickly using Tailwind CSS utility-first framework. Learn to build layouts, components, and interactive UI elements without writing custom CSS. Combine with HTML to create professional-grade web designs.",
        "topic": "Tailwind CSS",
        "questions": [
            {"q": "What type of CSS framework is Tailwind?", "options": ["Component-based", "Utility-first", "Grid-only", "Preprocessor"], "correct": 1},
            {"q": "How do you center items with Tailwind?", "options": ["center-x", "flex items-center justify-center", "align-center", "mx-auto my-auto only"], "correct": 1},
            {"q": "Which class adds padding in Tailwind?", "options": ["margin-4", "p-4", "pad-4", "spacing-4"], "correct": 1},
            {"q": "How do you make text large in Tailwind?", "options": ["font-large", "text-xl", "size-large", "text-big"], "correct": 1},
            {"q": "What prefix makes a style responsive in Tailwind?", "options": ["@media:", "resp:", "md:", "screen-md:"], "correct": 2},
        ],
    },
    {
        "title": "Brand Identity Design",
        "category": "DESIGN",
        "level": "INTERMEDIATE",
        "price": 39.99,
        "offersCertificate": True,
        "description": "Create comprehensive brand identities including logos, color palettes, typography systems, and brand guidelines. Learn the strategy behind successful brands and how design communicates values and personality. Build a brand identity from brief to final deliverable.",
        "topic": "Brand Identity",
        "questions": [
            {"q": "What is a brand identity?", "options": ["Just a logo", "Visual and verbal elements that represent a company", "A marketing campaign", "A website design"], "correct": 1},
            {"q": "What is a mood board used for?", "options": ["Planning timelines", "Gathering visual inspiration and direction", "Writing copy", "Calculating budgets"], "correct": 1},
            {"q": "What should a logo be?", "options": ["Complex and detailed", "Simple, memorable, and versatile", "Colorful as possible", "Text only"], "correct": 1},
            {"q": "What is a brand style guide?", "options": ["A user manual", "Documentation of brand's visual rules and usage", "A marketing plan", "A website map"], "correct": 1},
            {"q": "What is negative space in logo design?", "options": ["Dark colors", "Empty space used meaningfully in the design", "Background color", "White text"], "correct": 1},
        ],
    },
    {
        "title": "Illustration with Procreate",
        "category": "DESIGN",
        "level": "BEGINNER",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Create beautiful digital illustrations and artwork using Procreate on iPad. Learn brush techniques, layering, color theory, character design, and how to develop your unique illustration style. Perfect for artists transitioning to digital media.",
        "topic": "Digital Illustration",
        "questions": [
            {"q": "What device is Procreate designed for?", "options": ["Android tablet", "iPad", "Windows Surface", "Desktop PC"], "correct": 1},
            {"q": "What is a layer in Procreate?", "options": ["A brush setting", "An independent drawing surface stacked over others", "A color palette", "A canvas size"], "correct": 1},
            {"q": "What is the purpose of the Liquify tool?", "options": ["Adding color fills", "Warping and distorting artwork", "Creating text", "Exporting files"], "correct": 1},
            {"q": "What does clipping mask do in Procreate?", "options": ["Hides all layers", "Clips a layer's content to the shape below", "Merges layers", "Exports selection"], "correct": 1},
            {"q": "What is blending mode used for?", "options": ["Text formatting", "Controlling how layers interact with layers below", "Adjusting canvas size", "Setting brush size"], "correct": 1},
        ],
    },

    # BUSINESS (10)
    {
        "title": "Financial Planning Basics",
        "category": "BUSINESS",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Take control of your finances with essential budgeting, saving, and investment concepts. Learn to create personal financial plans, manage debt, and build an emergency fund. This course empowers you to make informed financial decisions.",
        "topic": "Financial Planning",
        "questions": [
            {"q": "What is the 50/30/20 budgeting rule?", "options": ["50% savings, 30% fun, 20% needs", "50% needs, 30% wants, 20% savings", "50% investment, 30% bills, 20% food", "50% debt, 30% savings, 20% rent"], "correct": 1},
            {"q": "What is compound interest?", "options": ["Interest on principal only", "Interest calculated on principal and accumulated interest", "A fixed interest rate", "A one-time fee"], "correct": 1},
            {"q": "What is an emergency fund?", "options": ["Insurance policy", "3-6 months of expenses saved for emergencies", "Investment portfolio", "Credit card limit"], "correct": 1},
            {"q": "What is diversification in investing?", "options": ["Putting all money in one stock", "Spreading investments across different assets to reduce risk", "Buying only bonds", "Only investing in gold"], "correct": 1},
            {"q": "What does ROI stand for?", "options": ["Rate of Inflation", "Return on Investment", "Risk of Income", "Revenue over Index"], "correct": 1},
        ],
    },
    {
        "title": "Entrepreneurship 101",
        "category": "BUSINESS",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Learn the fundamentals of starting and growing a successful business from idea validation to launch. Covers business models, market research, lean startup methodology, and funding options. Gain the entrepreneurial mindset to turn your vision into reality.",
        "topic": "Entrepreneurship",
        "questions": [
            {"q": "What is a value proposition?", "options": ["Company logo", "The unique benefit a product offers customers", "A pricing strategy", "A legal document"], "correct": 1},
            {"q": "What is the lean startup methodology?", "options": ["Cutting staff costs", "Build-measure-learn cycle for rapid iteration", "Avoiding investors", "Offshore manufacturing"], "correct": 1},
            {"q": "What is an MVP?", "options": ["Most Valuable Player", "Minimum Viable Product", "Maximum Value Proposal", "Market Validation Plan"], "correct": 1},
            {"q": "What is bootstrapping a startup?", "options": ["Seeking venture capital", "Funding a business using personal savings", "Crowdfunding", "Bank loans only"], "correct": 1},
            {"q": "What is a business model canvas?", "options": ["A financial statement", "A visual framework for describing a business model", "A marketing plan", "An org chart"], "correct": 1},
        ],
    },
    {
        "title": "Project Management Professional",
        "category": "BUSINESS",
        "level": "INTERMEDIATE",
        "price": 44.99,
        "offersCertificate": True,
        "description": "Master project management methodologies including Waterfall, Agile, and hybrid approaches. Learn scope management, scheduling, risk management, and stakeholder communication. Prepare for PMP certification and lead projects to successful completion.",
        "topic": "Project Management",
        "questions": [
            {"q": "What is a Gantt chart?", "options": ["A budget spreadsheet", "A bar chart showing project schedule over time", "A risk matrix", "A team org chart"], "correct": 1},
            {"q": "What does Agile emphasize?", "options": ["Detailed upfront planning", "Iterative delivery and adapting to change", "Fixed scope only", "Long release cycles"], "correct": 1},
            {"q": "What is a sprint in Scrum?", "options": ["A speed test", "A fixed time period to complete a set of work", "A daily standup", "A retrospective meeting"], "correct": 1},
            {"q": "What is a stakeholder?", "options": ["A shareholder only", "Anyone with interest in the project outcome", "The project manager", "A team member"], "correct": 1},
            {"q": "What does WBS stand for?", "options": ["Work Breakdown Structure", "Weekly Budget Statement", "Workflow Business Strategy", "Work Balance Sheet"], "correct": 0},
        ],
    },
    {
        "title": "Business Analytics",
        "category": "BUSINESS",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Harness data to drive business decisions using analytics tools and techniques. Learn Excel, SQL, and data visualization to transform raw data into actionable insights. Apply analytical thinking to solve real business problems.",
        "topic": "Business Analytics",
        "questions": [
            {"q": "What is KPI?", "options": ["Key Performance Indicator", "Key Process Improvement", "Knowledge Process Index", "Key Profit Income"], "correct": 0},
            {"q": "What is a pivot table used for?", "options": ["Data entry", "Summarizing and analyzing large datasets", "Creating charts only", "Writing reports"], "correct": 1},
            {"q": "What is A/B testing?", "options": ["Comparing two product versions with users", "Testing software bugs", "Accounting method", "API benchmark"], "correct": 0},
            {"q": "What does OLAP stand for?", "options": ["Online Logical Analysis Processing", "Online Analytical Processing", "Offline Linear Analysis Platform", "Optimized Layered Analytics Process"], "correct": 1},
            {"q": "What is a dashboard in analytics?", "options": ["A car speedometer", "A visual display of key metrics", "A database schema", "A report template"], "correct": 1},
        ],
    },
    {
        "title": "Leadership & Management",
        "category": "BUSINESS",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": False,
        "description": "Develop the leadership skills needed to inspire teams, manage conflict, and drive organizational success. Learn communication, delegation, emotional intelligence, and decision-making frameworks. Become the leader your team deserves.",
        "topic": "Leadership",
        "questions": [
            {"q": "What is emotional intelligence?", "options": ["IQ score", "Ability to understand and manage emotions", "Memory capacity", "Language fluency"], "correct": 1},
            {"q": "What is transformational leadership?", "options": ["Micromanaging", "Inspiring followers through vision and motivation", "Following strict rules", "Transactional rewards only"], "correct": 1},
            {"q": "What is delegation?", "options": ["Doing all tasks yourself", "Assigning tasks to team members", "Attending meetings", "Writing reports"], "correct": 1},
            {"q": "What is active listening?", "options": ["Multitasking during conversations", "Fully concentrating on and understanding the speaker", "Listening while typing", "Memorizing keywords"], "correct": 1},
            {"q": "What is a growth mindset?", "options": ["Believing abilities are fixed", "Believing abilities can be developed through effort", "Avoiding challenges", "Focusing on strengths only"], "correct": 1},
        ],
    },
    {
        "title": "Accounting Fundamentals",
        "category": "BUSINESS",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Learn the basics of accounting including the accounting equation, journal entries, ledgers, and financial statements. Understand how debits and credits work and how to read a balance sheet. Essential knowledge for business owners and finance professionals.",
        "topic": "Accounting",
        "questions": [
            {"q": "What is the accounting equation?", "options": ["Profit = Revenue - Costs", "Assets = Liabilities + Equity", "Income = Revenue - Tax", "Cash = Profit + Depreciation"], "correct": 1},
            {"q": "What is a debit in accounting?", "options": ["Always a decrease", "Left-side entry that can increase assets or decrease liabilities", "A bank fee", "A credit card charge"], "correct": 1},
            {"q": "What does GAAP stand for?", "options": ["General Accounting and Auditing Principles", "Generally Accepted Accounting Principles", "Global Annual Accounting Protocol", "Government Accounting Analysis Procedure"], "correct": 1},
            {"q": "What is depreciation?", "options": ["Profit decrease", "Allocating asset cost over its useful life", "Tax deduction only", "Currency devaluation"], "correct": 1},
            {"q": "What is a balance sheet?", "options": ["Income statement", "Snapshot of assets, liabilities, and equity at a point in time", "Cash flow report", "Budget document"], "correct": 1},
        ],
    },
    {
        "title": "Supply Chain Management",
        "category": "BUSINESS",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Understand end-to-end supply chain operations from procurement to delivery. Learn inventory management, logistics optimization, supplier relationships, and risk mitigation strategies. Gain skills to design resilient and efficient supply chains.",
        "topic": "Supply Chain",
        "questions": [
            {"q": "What is just-in-time inventory?", "options": ["Overstocking inventory", "Receiving goods only when needed", "Emergency restocking", "Automated ordering"], "correct": 1},
            {"q": "What is lead time?", "options": ["First item produced", "Time from order placement to delivery", "Time to hire staff", "Marketing lead generation time"], "correct": 1},
            {"q": "What does SKU stand for?", "options": ["Stock Keeping Unit", "Sales Key Utility", "Supply Knowledge Update", "Store Keeping Unit"], "correct": 0},
            {"q": "What is a procurement process?", "options": ["Selling process", "Process of obtaining goods and services", "Customer service process", "Financial reporting process"], "correct": 1},
            {"q": "What is the bullwhip effect?", "options": ["Efficient demand forecasting", "Small demand changes cause large supply chain fluctuations", "Fast delivery service", "Supplier negotiation tactic"], "correct": 1},
        ],
    },
    {
        "title": "Business Strategy",
        "category": "BUSINESS",
        "level": "ADVANCED",
        "price": 49.99,
        "offersCertificate": True,
        "description": "Master strategic thinking and frameworks used by top business consultants and executives. Learn Porter's Five Forces, SWOT analysis, Blue Ocean Strategy, and competitive positioning. Make better strategic decisions to sustain competitive advantage.",
        "topic": "Business Strategy",
        "questions": [
            {"q": "What is a SWOT analysis?", "options": ["Sales and workforce optimization tool", "Strengths, Weaknesses, Opportunities, Threats framework", "Software workflow tool", "Strategy without optimization tracking"], "correct": 1},
            {"q": "What is Porter's Five Forces?", "options": ["A leadership model", "Framework analyzing industry competition forces", "A pricing strategy", "A financial model"], "correct": 1},
            {"q": "What is competitive advantage?", "options": ["Having the lowest price always", "What makes a company superior to competitors in a sustainable way", "Having the most employees", "Being in multiple markets"], "correct": 1},
            {"q": "What is a Blue Ocean Strategy?", "options": ["Competing in existing markets", "Creating uncontested new market space", "Reducing costs only", "Merging with competitors"], "correct": 1},
            {"q": "What does M&A stand for?", "options": ["Marketing and Advertising", "Mergers and Acquisitions", "Management and Accounting", "Metrics and Analytics"], "correct": 1},
        ],
    },
    {
        "title": "Investment & Stocks",
        "category": "BUSINESS",
        "level": "BEGINNER",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Start investing with confidence by learning how stock markets work, how to analyze companies, and how to build a diversified portfolio. Covers ETFs, bonds, dividends, and risk management strategies for long-term wealth building.",
        "topic": "Investing",
        "questions": [
            {"q": "What is a stock?", "options": ["A company loan", "Ownership share in a company", "A government bond", "A savings account"], "correct": 1},
            {"q": "What is a P/E ratio?", "options": ["Profit to Equity", "Price to Earnings ratio", "Portfolio to Economy", "Profit and Expenses"], "correct": 1},
            {"q": "What is an ETF?", "options": ["Electronic Transfer Fund", "Exchange-Traded Fund", "Equity Trading Form", "External Trust Fund"], "correct": 1},
            {"q": "What does dollar-cost averaging mean?", "options": ["Averaging stock prices", "Investing fixed amounts regularly regardless of price", "Buying only when prices drop", "Averaging portfolio returns"], "correct": 1},
            {"q": "What is a dividend?", "options": ["A trading fee", "A portion of company profits paid to shareholders", "An interest payment", "A stock buyback"], "correct": 1},
        ],
    },
    {
        "title": "E-Commerce Business",
        "category": "BUSINESS",
        "level": "BEGINNER",
        "price": 24.99,
        "offersCertificate": False,
        "description": "Build and grow a profitable online store from scratch using Shopify, dropshipping, and digital marketing. Learn product research, store setup, payment processing, customer acquisition, and scaling strategies. Launch your e-commerce business today.",
        "topic": "E-Commerce",
        "questions": [
            {"q": "What is dropshipping?", "options": ["Shipping products by drone", "Selling without holding inventory, supplier ships directly", "Overnight delivery service", "Discounting products"], "correct": 1},
            {"q": "What is a conversion rate?", "options": ["Currency exchange rate", "Percentage of visitors who make a purchase", "Email open rate", "Ad click rate"], "correct": 1},
            {"q": "What is Shopify?", "options": ["A social media platform", "An e-commerce platform for building online stores", "An email marketing tool", "A payment processor only"], "correct": 1},
            {"q": "What is cart abandonment?", "options": ["Physical shopping cart left in store", "When customers add items but don't complete purchase", "Returning products", "Shopping app crash"], "correct": 1},
            {"q": "What does AOV stand for in e-commerce?", "options": ["Average Order Volume", "Average Order Value", "Annual Online Value", "Automated Order Verification"], "correct": 1},
        ],
    },

    # MARKETING (8)
    {
        "title": "Social Media Strategy",
        "category": "MARKETING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Build a winning social media presence for your brand or business across platforms like Instagram, TikTok, and LinkedIn. Learn content planning, audience growth strategies, and analytics to measure your success. Turn followers into loyal customers.",
        "topic": "Social Media Marketing",
        "questions": [
            {"q": "What is a content calendar?", "options": ["A holiday list", "A planned schedule of content to be published", "A subscriber list", "An email newsletter"], "correct": 1},
            {"q": "What is engagement rate?", "options": ["Number of followers", "Interactions divided by reach/impressions", "Ad spend", "Conversion rate"], "correct": 1},
            {"q": "What does UGC stand for?", "options": ["User Generated Content", "Unified Global Channels", "User Growth Criteria", "Unique Global Content"], "correct": 0},
            {"q": "What is a hashtag used for?", "options": ["Formatting text", "Categorizing and discovering content", "Direct messaging", "Paid promotion"], "correct": 1},
            {"q": "What is a social media algorithm?", "options": ["A bot account", "Rules determining what content users see", "A follower count tool", "A scheduling app"], "correct": 1},
        ],
    },
    {
        "title": "Content Marketing Mastery",
        "category": "MARKETING",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Create content that attracts, engages, and converts your target audience. Learn to plan content strategies, write compelling blogs, produce videos, and measure ROI. Build a content marketing machine that generates leads on autopilot.",
        "topic": "Content Marketing",
        "questions": [
            {"q": "What is content marketing?", "options": ["Paid advertising only", "Creating valuable content to attract and retain an audience", "Product marketing", "Cold calling"], "correct": 1},
            {"q": "What is a buyer persona?", "options": ["A real customer", "A semi-fictional representation of your ideal customer", "A sales rep", "A content template"], "correct": 1},
            {"q": "What is a content audit?", "options": ["Financial audit", "Reviewing existing content for performance and gaps", "Grammar check", "Legal review"], "correct": 1},
            {"q": "What is pillar content?", "options": ["Short social posts", "Comprehensive content around a core topic", "Paid content", "Email newsletters"], "correct": 1},
            {"q": "What is a CTA in marketing?", "options": ["Content Traffic Analysis", "Call to Action", "Campaign Target Audience", "Click-Through Average"], "correct": 1},
        ],
    },
    {
        "title": "Google Ads Mastery",
        "category": "MARKETING",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": True,
        "description": "Run profitable Google Ads campaigns from keyword research to ad copywriting and optimization. Learn search, display, shopping, and YouTube campaign types with bidding strategies and conversion tracking. Get more customers with every dollar spent.",
        "topic": "Google Ads",
        "questions": [
            {"q": "What is Quality Score in Google Ads?", "options": ["Budget score", "Rating of ad relevance and landing page quality", "Impression count", "Click-through rate only"], "correct": 1},
            {"q": "What is a negative keyword?", "options": ["An offensive keyword", "A keyword that prevents your ad from showing", "A low-performing keyword", "A broad match keyword"], "correct": 1},
            {"q": "What does CPC stand for?", "options": ["Cost Per Click", "Campaign Performance Check", "Content Per Channel", "Click Payment Count"], "correct": 0},
            {"q": "What is remarketing in Google Ads?", "options": ["Rewriting ads", "Showing ads to previous website visitors", "Increasing budget", "Changing bid strategy"], "correct": 1},
            {"q": "What is an ad extension?", "options": ["Extra time to pay", "Additional info shown with ads like sitelinks", "An extended ad campaign", "A Google Analytics feature"], "correct": 1},
        ],
    },
    {
        "title": "Email Marketing",
        "category": "MARKETING",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Build and grow an email list and send campaigns that get opened, clicked, and convert. Learn list building, segmentation, automation sequences, and A/B testing to maximize your email ROI. Email marketing delivers the highest ROI of any channel.",
        "topic": "Email Marketing",
        "questions": [
            {"q": "What is email open rate?", "options": ["Total emails sent", "Percentage of recipients who opened the email", "Emails delivered", "Unsubscribe rate"], "correct": 1},
            {"q": "What is an email drip campaign?", "options": ["Spam campaign", "Automated series of emails sent over time", "One-time newsletter", "Cold email sequence"], "correct": 1},
            {"q": "What does segmentation in email marketing mean?", "options": ["Deleting subscribers", "Dividing email list into groups for targeted messaging", "Scheduling emails", "Testing subject lines"], "correct": 1},
            {"q": "What is a double opt-in?", "options": ["Two email addresses", "Subscriber confirms via a confirmation email", "Two emails per day", "Paid subscription"], "correct": 1},
            {"q": "What does GDPR require for email marketing?", "options": ["Paid advertising only", "Explicit consent from recipients before sending emails", "Monthly unsubscribe reminders", "Email encryption only"], "correct": 1},
        ],
    },
    {
        "title": "SEO Fundamentals",
        "category": "MARKETING",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Learn how search engines work and how to optimize your website to rank higher in Google. Covers keyword research, on-page SEO, link building, and technical SEO fundamentals. Drive organic traffic to your website without paying for ads.",
        "topic": "SEO",
        "questions": [
            {"q": "What does SEO stand for?", "options": ["Social Engagement Optimization", "Search Engine Optimization", "Site Enhancement Operations", "Search Entry Order"], "correct": 1},
            {"q": "What is a backlink?", "options": ["A broken link", "A link from another website to yours", "An internal link", "A social media link"], "correct": 1},
            {"q": "What is keyword density?", "options": ["Number of keywords in title", "How often a keyword appears relative to total word count", "Keyword search volume", "Keyword difficulty score"], "correct": 1},
            {"q": "What is a meta description?", "options": ["A page title", "A brief description of a page shown in search results", "An image caption", "A header tag"], "correct": 1},
            {"q": "What is domain authority?", "options": ["Website ownership", "A score predicting how likely a domain is to rank", "Server uptime", "Domain registration"], "correct": 1},
        ],
    },
    {
        "title": "Digital Marketing Complete",
        "category": "MARKETING",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Master all digital marketing channels including SEO, social media, email, content marketing, and paid advertising. Learn to build integrated campaigns, measure performance with analytics, and optimize for growth. Become a complete digital marketer.",
        "topic": "Digital Marketing",
        "questions": [
            {"q": "What is the marketing funnel?", "options": ["A sales technique", "Stages from awareness to purchase and loyalty", "A funnel chart tool", "A content template"], "correct": 1},
            {"q": "What is CPM in digital advertising?", "options": ["Cost Per Month", "Cost Per Thousand impressions", "Clicks Per Minute", "Campaign Performance Metric"], "correct": 1},
            {"q": "What is customer lifetime value?", "options": ["Annual revenue per customer", "Total revenue expected from a customer over their relationship", "First purchase value", "Monthly subscription fee"], "correct": 1},
            {"q": "What is influencer marketing?", "options": ["Paying celebrities only", "Partnering with individuals who influence target audiences", "Banner advertising", "Email campaigns"], "correct": 1},
            {"q": "What is attribution in marketing?", "options": ["Giving credit to team members", "Identifying which touchpoints contributed to conversions", "Content creation", "Budget allocation"], "correct": 1},
        ],
    },
    {
        "title": "Copywriting that Converts",
        "category": "MARKETING",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": False,
        "description": "Write persuasive copy that compels readers to take action — whether buying, clicking, or subscribing. Learn proven copywriting formulas, headlines, storytelling, and objection handling. Apply these skills to ads, emails, landing pages, and websites.",
        "topic": "Copywriting",
        "questions": [
            {"q": "What is AIDA in copywriting?", "options": ["Audience, Impact, Design, Action", "Attention, Interest, Desire, Action", "Awareness, Influence, Demand, Acquisition", "Attract, Inform, Deliver, Automate"], "correct": 1},
            {"q": "What is a headline's primary job?", "options": ["Provide all information", "Get the reader to read the next line", "Include keywords", "Explain the offer"], "correct": 1},
            {"q": "What is social proof in copywriting?", "options": ["Social media posts", "Testimonials and evidence that others trust you", "Legal proof", "Social media ads"], "correct": 1},
            {"q": "What is a USP?", "options": ["Universal Service Protocol", "Unique Selling Proposition", "User Support Plan", "Unlimited Sales Potential"], "correct": 1},
            {"q": "What is the purpose of urgency in copy?", "options": ["Annoy readers", "Motivate immediate action", "Extend the offer", "Confuse competitors"], "correct": 1},
        ],
    },
    {
        "title": "Affiliate Marketing",
        "category": "MARKETING",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Learn how to earn passive income by promoting other companies' products and earning commissions. Discover how to choose profitable niches, build an audience, create content that converts, and scale your affiliate income. Start earning online today.",
        "topic": "Affiliate Marketing",
        "questions": [
            {"q": "What is affiliate marketing?", "options": ["Creating your own products", "Earning commissions by promoting others' products", "Social media marketing", "Email marketing"], "correct": 1},
            {"q": "What is an affiliate link?", "options": ["A social media link", "A tracked link that credits you for referred sales", "A backlink", "A paid ad link"], "correct": 1},
            {"q": "What is a commission?", "options": ["A flat fee", "A percentage of sale paid to the affiliate", "A monthly retainer", "A signup bonus"], "correct": 1},
            {"q": "What is niche selection in affiliate marketing?", "options": ["Choosing a company", "Focusing on a specific topic or audience segment", "Selecting a payment method", "Picking an ad format"], "correct": 1},
            {"q": "What does EPC stand for?", "options": ["Email Per Click", "Earnings Per Click", "Effective Promotion Cost", "Email Promotion Campaign"], "correct": 1},
        ],
    },

    # SCIENCE (8)
    {
        "title": "Physics for Engineers",
        "category": "SCIENCE",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": False,
        "description": "Master the physics principles essential for engineering applications including mechanics, thermodynamics, waves, and electromagnetism. Solve real-world engineering problems using mathematical models and physical reasoning. Build the scientific foundation for your engineering career.",
        "topic": "Physics",
        "questions": [
            {"q": "What is Newton's second law of motion?", "options": ["F = mv", "F = ma", "E = mc²", "P = mv"], "correct": 1},
            {"q": "What is the unit of electric current?", "options": ["Volt", "Ohm", "Ampere", "Watt"], "correct": 2},
            {"q": "What is kinetic energy?", "options": ["Energy stored in position", "Energy of motion", "Chemical energy", "Thermal energy"], "correct": 1},
            {"q": "What does the law of conservation of energy state?", "options": ["Energy is always lost", "Energy cannot be created or destroyed", "Energy always increases", "Kinetic and potential energy are equal"], "correct": 1},
            {"q": "What is the unit of power?", "options": ["Joule", "Newton", "Watt", "Pascal"], "correct": 2},
        ],
    },
    {
        "title": "Chemistry Basics",
        "category": "SCIENCE",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Explore the fundamental concepts of chemistry including atomic structure, periodic table, chemical bonding, and reactions. Learn to balance equations, understand stoichiometry, and grasp acid-base chemistry. Build a solid foundation for further studies in science.",
        "topic": "Chemistry",
        "questions": [
            {"q": "What is an atom?", "options": ["A molecule", "The smallest unit of an element", "A compound", "A cell"], "correct": 1},
            {"q": "What is the atomic number?", "options": ["Number of neutrons", "Number of protons in the nucleus", "Atomic mass", "Number of electrons only"], "correct": 1},
            {"q": "What type of bond shares electrons?", "options": ["Ionic bond", "Hydrogen bond", "Covalent bond", "Metallic bond"], "correct": 2},
            {"q": "What is a chemical reaction?", "options": ["Physical change only", "Process where substances transform into new substances", "Mixing two liquids", "Melting a solid"], "correct": 1},
            {"q": "What is pH a measure of?", "options": ["Temperature", "Acidity or alkalinity of a solution", "Concentration in grams", "Reaction speed"], "correct": 1},
        ],
    },
    {
        "title": "Biology Cell Biology",
        "category": "SCIENCE",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Explore the cell as the fundamental unit of life, covering cell structure, organelles, cell division, and cellular respiration. Understand how cells communicate, differentiate, and how errors lead to disease. Essential for students of medicine, biology, and biotechnology.",
        "topic": "Cell Biology",
        "questions": [
            {"q": "What is the powerhouse of the cell?", "options": ["Nucleus", "Ribosome", "Mitochondria", "Golgi apparatus"], "correct": 2},
            {"q": "What is DNA made of?", "options": ["Amino acids", "Nucleotides", "Fatty acids", "Glucose"], "correct": 1},
            {"q": "What is mitosis?", "options": ["Cell death", "Cell division producing two identical cells", "Protein synthesis", "Cell respiration"], "correct": 1},
            {"q": "What does the cell membrane do?", "options": ["Produces energy", "Controls what enters and exits the cell", "Stores genetic information", "Makes proteins"], "correct": 1},
            {"q": "What is ATP?", "options": ["A type of protein", "The cell's primary energy currency", "A lipid molecule", "A genetic marker"], "correct": 1},
        ],
    },
    {
        "title": "Environmental Science",
        "category": "SCIENCE",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Understand the complex relationship between humans and the natural environment. Learn about ecosystems, biodiversity, climate change, pollution, and sustainable development. Gain the knowledge to make environmentally informed decisions and contribute to a sustainable future.",
        "topic": "Environmental Science",
        "questions": [
            {"q": "What is the greenhouse effect?", "options": ["Growing plants in greenhouses", "Trapping of heat in atmosphere by greenhouse gases", "Deforestation effect", "Ozone layer formation"], "correct": 1},
            {"q": "What is biodiversity?", "options": ["Number of humans on Earth", "Variety of life in an ecosystem or on Earth", "Animal population count", "Plant species count"], "correct": 1},
            {"q": "What is carbon footprint?", "options": ["Fossil fuel size", "Total greenhouse gas emissions caused by an individual or activity", "Carbon reserves", "Carbon trading"], "correct": 1},
            {"q": "What is an ecosystem?", "options": ["A single organism", "A community of organisms and their physical environment", "A food chain only", "A nature reserve"], "correct": 1},
            {"q": "What is renewable energy?", "options": ["Nuclear power only", "Energy from sources that naturally replenish", "Coal and oil", "Energy stored in batteries"], "correct": 1},
        ],
    },
    {
        "title": "Astronomy & Cosmology",
        "category": "SCIENCE",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Journey through the universe exploring planets, stars, galaxies, black holes, and the Big Bang. Learn how astronomers observe the cosmos, the life cycle of stars, and the latest theories about dark matter and dark energy. Satisfy your cosmic curiosity.",
        "topic": "Astronomy",
        "questions": [
            {"q": "What is a light-year?", "options": ["Time it takes light to orbit Earth", "Distance light travels in one year", "Speed of light", "Age of the universe unit"], "correct": 1},
            {"q": "What is a black hole?", "options": ["A dark star", "A region where gravity is so strong nothing can escape", "A collapsed galaxy", "A type of nebula"], "correct": 1},
            {"q": "What is the Big Bang theory?", "options": ["Stars exploding", "The origin of the universe from a single dense state", "A galaxy collision", "A star formation theory"], "correct": 1},
            {"q": "How long does light take to travel from the Sun to Earth?", "options": ["1 second", "8 minutes", "1 hour", "1 day"], "correct": 1},
            {"q": "What is the Milky Way?", "options": ["A constellation", "Our home galaxy", "A solar system", "A nebula"], "correct": 1},
        ],
    },
    {
        "title": "Neuroscience Introduction",
        "category": "SCIENCE",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": True,
        "description": "Explore how the brain and nervous system work at cellular, circuit, and systems levels. Learn about neurons, synapses, brain regions, cognition, and neurological disorders. This course bridges biology, psychology, and medicine in a fascinating journey through the mind.",
        "topic": "Neuroscience",
        "questions": [
            {"q": "What is a neuron?", "options": ["A brain region", "A nerve cell that transmits information", "A brain hormone", "A type of synapse"], "correct": 1},
            {"q": "What does a synapse do?", "options": ["Stores memories", "Transmits signals between neurons", "Controls motor function", "Filters blood"], "correct": 1},
            {"q": "What is the function of the hippocampus?", "options": ["Controls vision", "Plays a key role in memory formation", "Regulates heartbeat", "Processes sound"], "correct": 1},
            {"q": "What is a neurotransmitter?", "options": ["A brain scan", "A chemical messenger between neurons", "A type of neuron", "A brain wave"], "correct": 1},
            {"q": "What is neuroplasticity?", "options": ["Brain surgery technique", "The brain's ability to reorganize and form new connections", "Neuron rigidity", "Memory storage limit"], "correct": 1},
        ],
    },
    {
        "title": "Genetics & DNA",
        "category": "SCIENCE",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": False,
        "description": "Understand the molecular basis of heredity from DNA structure and replication to gene expression and genetic engineering. Learn Mendelian genetics, mutations, CRISPR, and the Human Genome Project. Essential for students in biology and medicine.",
        "topic": "Genetics",
        "questions": [
            {"q": "What does DNA stand for?", "options": ["Deoxyribose Nucleic Acid", "Deoxyribonucleic Acid", "Double Nucleotide Array", "Directed Nucleic Algorithm"], "correct": 1},
            {"q": "What is a gene?", "options": ["A chromosome", "A segment of DNA encoding a specific protein", "A cell nucleus", "An RNA molecule"], "correct": 1},
            {"q": "What is DNA replication?", "options": ["DNA destruction", "The process of copying DNA before cell division", "Protein synthesis", "Gene mutation"], "correct": 1},
            {"q": "What is a dominant allele?", "options": ["A recessive trait", "An allele whose trait is expressed over recessive alleles", "A mutated gene", "An X chromosome"], "correct": 1},
            {"q": "What is CRISPR used for?", "options": ["Microscopy imaging", "Precise gene editing", "DNA sequencing only", "Protein folding"], "correct": 1},
        ],
    },
    {
        "title": "Quantum Physics Basics",
        "category": "SCIENCE",
        "level": "ADVANCED",
        "price": 39.99,
        "offersCertificate": True,
        "description": "Delve into the strange and fascinating world of quantum mechanics, where particles behave in ways that defy classical intuition. Learn about wave-particle duality, superposition, entanglement, and the Heisenberg uncertainty principle. Explore the physics that underlies all matter.",
        "topic": "Quantum Physics",
        "questions": [
            {"q": "What is wave-particle duality?", "options": ["Waves becoming particles", "Quantum objects exhibit both wave and particle properties", "Particle splitting", "Wave interference only"], "correct": 1},
            {"q": "What is quantum superposition?", "options": ["Stacking quantum computers", "A particle existing in multiple states simultaneously", "Two particles colliding", "Quantum entanglement"], "correct": 1},
            {"q": "What is the Heisenberg uncertainty principle?", "options": ["Energy is always conserved", "Cannot know exact position and momentum simultaneously", "Particles never interact", "Light speed is constant"], "correct": 1},
            {"q": "What is quantum entanglement?", "options": ["Particles tangled physically", "Correlated particles where measuring one affects the other instantly", "Wave interference", "Atomic bonding"], "correct": 1},
            {"q": "What is Schrödinger's cat thought experiment about?", "options": ["Cat physics", "Quantum superposition and observation effects on quantum states", "Animal behavior", "Relativity"], "correct": 1},
        ],
    },

    # MATH (8)
    {
        "title": "Calculus Made Simple",
        "category": "MATH",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Conquer calculus through clear explanations and step-by-step problem solving covering limits, derivatives, integrals, and their applications. Learn to analyze rates of change and accumulation in real-world contexts. Perfect for students and professionals needing calculus for engineering or science.",
        "topic": "Calculus",
        "questions": [
            {"q": "What is a derivative?", "options": ["An integral", "The rate of change of a function", "A limit value", "A polynomial"], "correct": 1},
            {"q": "What is the derivative of x²?", "options": ["x", "2x", "x²/2", "2"], "correct": 1},
            {"q": "What does an integral compute?", "options": ["Rate of change", "Area under a curve", "Slope of tangent", "Maximum value"], "correct": 1},
            {"q": "What is the fundamental theorem of calculus?", "options": ["Derivatives and integrals are unrelated", "Differentiation and integration are inverse operations", "Limits always exist", "All functions are differentiable"], "correct": 1},
            {"q": "What is a limit?", "options": ["The maximum value of a function", "The value a function approaches as input approaches a value", "The derivative at a point", "The area under a curve"], "correct": 1},
        ],
    },
    {
        "title": "Statistics for Data Science",
        "category": "MATH",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": True,
        "description": "Build the statistical foundation essential for data science including probability, distributions, hypothesis testing, regression, and Bayesian thinking. Learn to draw valid conclusions from data and avoid common statistical pitfalls. Apply statistics using Python.",
        "topic": "Statistics",
        "questions": [
            {"q": "What is the central limit theorem?", "options": ["All data is normally distributed", "Sample means approach normal distribution as sample size grows", "Variance always equals 1", "Mean equals median always"], "correct": 1},
            {"q": "What is a p-value?", "options": ["Probability of the hypothesis being true", "Probability of observing results if null hypothesis is true", "Power of the test", "Prediction value"], "correct": 1},
            {"q": "What is standard deviation?", "options": ["Average value", "Measure of spread around the mean", "Median value", "Maximum minus minimum"], "correct": 1},
            {"q": "What is a normal distribution?", "options": ["Any bell-shaped data", "Symmetric bell curve defined by mean and standard deviation", "Uniform distribution", "Skewed distribution"], "correct": 1},
            {"q": "What is correlation?", "options": ["Causation", "Measure of linear relationship between two variables", "Regression coefficient", "Variance ratio"], "correct": 1},
        ],
    },
    {
        "title": "Linear Algebra",
        "category": "MATH",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Master vectors, matrices, linear transformations, eigenvalues, and decompositions that form the mathematical backbone of machine learning and computer graphics. Build geometric intuition alongside algebraic computation. Essential for data scientists and engineers.",
        "topic": "Linear Algebra",
        "questions": [
            {"q": "What is a vector?", "options": ["A single number", "A quantity with both magnitude and direction", "A matrix row", "A scalar value"], "correct": 1},
            {"q": "What is matrix multiplication?", "options": ["Element-wise product", "Dot product of rows and columns", "Adding matrices", "Scaling a matrix"], "correct": 1},
            {"q": "What is an eigenvalue?", "options": ["Matrix determinant", "Scalar that scales eigenvectors under a transformation", "Matrix rank", "Vector magnitude"], "correct": 1},
            {"q": "What is the determinant used for?", "options": ["Adding matrices", "Finding if a matrix is invertible", "Multiplying vectors", "Scaling matrices"], "correct": 1},
            {"q": "What is a linear transformation?", "options": ["A line equation", "A function that maps vectors while preserving linearity", "Matrix inversion", "A dot product"], "correct": 1},
        ],
    },
    {
        "title": "Discrete Mathematics",
        "category": "MATH",
        "level": "INTERMEDIATE",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Study the mathematics of discrete structures foundational to computer science including logic, set theory, graph theory, combinatorics, and proof techniques. Learn to think rigorously and construct mathematical proofs. Essential for computer science students.",
        "topic": "Discrete Mathematics",
        "questions": [
            {"q": "What is a set?", "options": ["An ordered list", "An unordered collection of distinct elements", "A sequence", "A function"], "correct": 1},
            {"q": "What is a graph in discrete math?", "options": ["A line chart", "A structure of vertices connected by edges", "A matrix", "A number set"], "correct": 1},
            {"q": "What is a proposition in logic?", "options": ["A question", "A statement that is either true or false", "A command", "A definition"], "correct": 1},
            {"q": "What is combinatorics?", "options": ["Study of algorithms", "Study of counting arrangements and selections", "Study of continuous functions", "Study of equations"], "correct": 1},
            {"q": "What is a proof by induction?", "options": ["Proving all cases directly", "Proving base case and inductive step to prove for all n", "Contradiction proof", "Direct substitution"], "correct": 1},
        ],
    },
    {
        "title": "Probability Theory",
        "category": "MATH",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Develop a deep understanding of probability including random variables, distributions, expected value, and stochastic processes. Learn Bayes' theorem, the law of large numbers, and their applications in statistics and machine learning. Reason under uncertainty with confidence.",
        "topic": "Probability",
        "questions": [
            {"q": "What is probability?", "options": ["A certainty measure", "A measure of how likely an event is to occur", "A statistical average", "A frequency count"], "correct": 1},
            {"q": "What is Bayes' theorem used for?", "options": ["Computing integrals", "Updating probabilities based on new evidence", "Finding expected value", "Computing variance"], "correct": 1},
            {"q": "What is the expected value?", "options": ["Most likely outcome", "Weighted average of all possible outcomes", "Maximum outcome", "Median outcome"], "correct": 1},
            {"q": "What are mutually exclusive events?", "options": ["Events that always occur together", "Events that cannot occur simultaneously", "Independent events", "Complementary events"], "correct": 1},
            {"q": "What is a random variable?", "options": ["An unpredictable constant", "A variable whose value is determined by a random process", "A test statistic", "A probability distribution"], "correct": 1},
        ],
    },
    {
        "title": "Number Theory",
        "category": "MATH",
        "level": "ADVANCED",
        "price": 29.99,
        "offersCertificate": True,
        "description": "Explore the beautiful mathematics of integers including divisibility, prime numbers, modular arithmetic, and cryptographic applications. Discover Fermat's little theorem, Euler's theorem, and the foundations of public-key cryptography. A gem of pure mathematics with modern applications.",
        "topic": "Number Theory",
        "questions": [
            {"q": "What is a prime number?", "options": ["Any odd number", "A number divisible only by 1 and itself", "An even number greater than 2", "A perfect square"], "correct": 1},
            {"q": "What is the GCD?", "options": ["Greatest Common Divisor", "General Computation Degree", "Generalized Complex Division", "Graph Connectivity Degree"], "correct": 0},
            {"q": "What is modular arithmetic?", "options": ["Division without remainders", "Arithmetic of remainders after division", "Floating point arithmetic", "Matrix arithmetic"], "correct": 1},
            {"q": "What is Fermat's little theorem used for?", "options": ["Prime factorization", "Primality testing and cryptography", "Finding GCD", "Computing logarithms"], "correct": 1},
            {"q": "What is the Fundamental Theorem of Arithmetic?", "options": ["Every number is prime", "Every integer > 1 has unique prime factorization", "Primes are infinite", "Every odd number is prime"], "correct": 1},
        ],
    },
    {
        "title": "Differential Equations",
        "category": "MATH",
        "level": "ADVANCED",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Solve ordinary and partial differential equations that model real-world phenomena in physics, engineering, and biology. Learn first-order equations, linear systems, Laplace transforms, and numerical methods. Apply differential equations to model population growth, heat transfer, and oscillations.",
        "topic": "Differential Equations",
        "questions": [
            {"q": "What is a differential equation?", "options": ["An algebraic equation", "An equation involving derivatives of a function", "A statistical model", "A calculus integral"], "correct": 1},
            {"q": "What is a first-order ODE?", "options": ["An equation with no derivatives", "An equation with first-order derivatives only", "An equation with second derivatives", "A partial differential equation"], "correct": 1},
            {"q": "What is the Laplace transform used for?", "options": ["Integration only", "Solving differential equations by converting to algebraic equations", "Finding derivatives", "Probability calculations"], "correct": 1},
            {"q": "What is an initial value problem?", "options": ["An equation without solutions", "A differential equation with specified initial conditions", "An equation with boundary conditions only", "A numerical method"], "correct": 1},
            {"q": "What is Euler's method?", "options": ["Exact analytical solution", "Numerical approximation of ODE solutions", "Laplace transform technique", "Fourier analysis method"], "correct": 1},
        ],
    },
    {
        "title": "Applied Mathematics",
        "category": "MATH",
        "level": "ADVANCED",
        "price": 39.99,
        "offersCertificate": True,
        "description": "Apply mathematical tools including optimization, Fourier analysis, numerical methods, and mathematical modeling to solve complex real-world engineering and scientific problems. Develop the ability to formulate and solve practical problems using rigorous mathematical frameworks.",
        "topic": "Applied Mathematics",
        "questions": [
            {"q": "What is optimization in mathematics?", "options": ["Finding the average", "Finding the maximum or minimum of a function", "Solving linear equations", "Computing derivatives"], "correct": 1},
            {"q": "What is a Fourier transform?", "options": ["A matrix operation", "Decomposing a signal into frequency components", "A differential equation technique", "A numerical integration method"], "correct": 1},
            {"q": "What is numerical analysis?", "options": ["Exact mathematical proofs", "Algorithms for approximating solutions to mathematical problems", "Number theory applications", "Statistical analysis"], "correct": 1},
            {"q": "What is a mathematical model?", "options": ["A physical model", "A mathematical description of a real-world system", "A computer simulation only", "A statistical forecast"], "correct": 1},
            {"q": "What is linear programming?", "options": ["Computer programming", "Optimizing a linear objective subject to linear constraints", "Graph theory method", "Solving differential equations"], "correct": 1},
        ],
    },

    # LANGUAGE (8)
    {
        "title": "French for Beginners",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Start speaking French from day one with practical vocabulary, basic grammar, and essential phrases for travel and daily life. Learn pronunciation, numbers, greetings, and how to form simple sentences. Begin your French journey with confidence and enthusiasm.",
        "topic": "French Language",
        "questions": [
            {"q": "How do you say 'hello' in French?", "options": ["Ciao", "Hola", "Bonjour", "Guten Tag"], "correct": 2},
            {"q": "What is the French word for 'cat'?", "options": ["Chien", "Chat", "Cheval", "Cochon"], "correct": 1},
            {"q": "How do you say 'thank you' in French?", "options": ["Danke", "Gracias", "Merci", "Arigatou"], "correct": 2},
            {"q": "What gender is 'la maison' in French?", "options": ["Masculine", "Feminine", "Neutral", "Variable"], "correct": 1},
            {"q": "How do you say 'I love you' in French?", "options": ["Je t'adore", "Je t'aime", "Je vous aime", "Tu m'aimes"], "correct": 1},
        ],
    },
    {
        "title": "English Business Writing",
        "category": "LANGUAGE",
        "level": "INTERMEDIATE",
        "price": 19.99,
        "offersCertificate": True,
        "description": "Develop professional English writing skills for emails, reports, proposals, and presentations in business contexts. Learn clarity, tone, structure, and persuasive techniques that communicate effectively. Stand out in international business with polished written communication.",
        "topic": "Business English",
        "questions": [
            {"q": "What is the correct tone for professional emails?", "options": ["Casual and informal", "Formal and respectful", "Very emotional", "Abbreviated text-speak"], "correct": 1},
            {"q": "What should a business email subject line be?", "options": ["Long and descriptive", "Clear and concise", "Left blank", "In all caps"], "correct": 1},
            {"q": "What is an executive summary?", "options": ["A detailed report", "A concise overview of a longer document", "A financial statement", "A team introduction"], "correct": 1},
            {"q": "Which is the correct salutation for an unknown recipient?", "options": ["Hey there,", "To Whom It May Concern,", "What's up,", "Yo,"], "correct": 1},
            {"q": "What does 'CC' mean in email?", "options": ["Carbon Copy", "Content Creator", "Closed Caption", "Customer Contact"], "correct": 0},
        ],
    },
    {
        "title": "Spanish from Zero",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Learn Spanish quickly with a communicative approach focusing on real conversations from the very start. Master essential vocabulary, verb conjugations, and cultural insights about Spanish-speaking countries. Build confidence to speak Spanish in everyday situations.",
        "topic": "Spanish Language",
        "questions": [
            {"q": "How do you say 'good morning' in Spanish?", "options": ["Buenas noches", "Buenos días", "Buenas tardes", "Hola"], "correct": 1},
            {"q": "What is the Spanish word for 'water'?", "options": ["Agua", "Fuego", "Tierra", "Viento"], "correct": 0},
            {"q": "How do you say 'my name is' in Spanish?", "options": ["Me llamo", "Yo soy", "Mi nombre es", "Both A and C"], "correct": 3},
            {"q": "What are the two verbs for 'to be' in Spanish?", "options": ["Ser and estar", "Tener and haber", "Ir and venir", "Hablar and hablar"], "correct": 0},
            {"q": "How do you say 'please' in Spanish?", "options": ["Gracias", "De nada", "Por favor", "Perdón"], "correct": 2},
        ],
    },
    {
        "title": "Mandarin Chinese Basics",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Begin your Mandarin Chinese journey with Pinyin pronunciation, basic tones, essential vocabulary, and simple sentence patterns. Learn to introduce yourself, count, and navigate everyday situations in Chinese. Gain a solid foundation for one of the world's most spoken languages.",
        "topic": "Mandarin Chinese",
        "questions": [
            {"q": "How many tones does Mandarin Chinese have?", "options": ["2", "3", "4", "5"], "correct": 2},
            {"q": "What does 'nǐ hǎo' mean?", "options": ["Goodbye", "Thank you", "Hello", "How are you"], "correct": 2},
            {"q": "What is Pinyin?", "options": ["Chinese characters", "Romanization system for Mandarin pronunciation", "A dialect", "A writing style"], "correct": 1},
            {"q": "How do you say 'thank you' in Mandarin?", "options": ["Bù kèqì", "Xièxiè", "Duìbuqǐ", "Zàijiàn"], "correct": 1},
            {"q": "What does '我' (wǒ) mean?", "options": ["You", "He/She", "I/Me", "We"], "correct": 2},
        ],
    },
    {
        "title": "German Language Course",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Start speaking German with practical phrases, grammar fundamentals, and cultural knowledge. Learn noun genders, verb conjugations, and sentence structure through communicative exercises. German opens doors to careers in Europe's largest economy.",
        "topic": "German Language",
        "questions": [
            {"q": "How do you say 'good evening' in German?", "options": ["Guten Morgen", "Guten Tag", "Guten Abend", "Gute Nacht"], "correct": 2},
            {"q": "How many grammatical genders does German have?", "options": ["1", "2", "3", "4"], "correct": 2},
            {"q": "What does 'danke' mean?", "options": ["Please", "Sorry", "Hello", "Thank you"], "correct": 3},
            {"q": "How do you say 'I speak German' in German?", "options": ["Ich spreche Deutsch", "Ich bin Deutsch", "Ich lerne Deutsch", "Ich verstehe Deutsch"], "correct": 0},
            {"q": "What is the German article for feminine nouns?", "options": ["der", "die", "das", "den"], "correct": 1},
        ],
    },
    {
        "title": "Italian for Travelers",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Learn the essential Italian phrases, vocabulary, and cultural etiquette needed for a wonderful trip to Italy. Cover restaurant conversations, directions, shopping, and hotel interactions. Speak enough Italian to connect with locals and enhance your Italian experience.",
        "topic": "Italian Language",
        "questions": [
            {"q": "How do you say 'where is...' in Italian?", "options": ["Quanto costa?", "Come stai?", "Dov'è...?", "Cosa vuoi?"], "correct": 2},
            {"q": "What does 'per favore' mean?", "options": ["Thank you", "Please", "Excuse me", "You're welcome"], "correct": 1},
            {"q": "How do you say 'the bill please' in Italian?", "options": ["Il menu per favore", "Il conto per favore", "Un caffè per favore", "L'acqua per favore"], "correct": 1},
            {"q": "What does 'buongiorno' mean?", "options": ["Good night", "Good evening", "Good morning/Good day", "Goodbye"], "correct": 2},
            {"q": "How do you say 'how much does it cost?'", "options": ["Come ti chiami?", "Quanto costa?", "Dove sei?", "Che cosa è?"], "correct": 1},
        ],
    },
    {
        "title": "Arabic Script & Reading",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Learn to read and write the Arabic script from scratch, mastering all 28 letters and their different forms. Practice reading real Arabic words and understand the right-to-left writing system. Build the foundation to learn any Arabic dialect or Modern Standard Arabic.",
        "topic": "Arabic Language",
        "questions": [
            {"q": "In which direction is Arabic written?", "options": ["Left to right", "Right to left", "Top to bottom", "Bottom to top"], "correct": 1},
            {"q": "How many letters are in the Arabic alphabet?", "options": ["24", "26", "28", "30"], "correct": 2},
            {"q": "What are harakat in Arabic?", "options": ["Arabic letters", "Vowel diacritical marks", "Punctuation marks", "Special characters"], "correct": 1},
            {"q": "Do Arabic letters change form?", "options": ["No, always same form", "Yes, depending on position in the word", "Only for long vowels", "Only in formal writing"], "correct": 1},
            {"q": "What does 'مرحبا' mean?", "options": ["Goodbye", "Thank you", "Hello", "Please"], "correct": 2},
        ],
    },
    {
        "title": "Japanese Hiragana",
        "category": "LANGUAGE",
        "level": "BEGINNER",
        "price": 9.99,
        "offersCertificate": False,
        "description": "Master the Hiragana writing system, one of three Japanese scripts, through systematic practice and mnemonics. Learn all 46 base characters plus diacritics and combination sounds. Gain the ability to read and write basic Japanese and unlock the gateway to the language.",
        "topic": "Japanese Language",
        "questions": [
            {"q": "How many base Hiragana characters are there?", "options": ["26", "36", "46", "56"], "correct": 2},
            {"q": "What is Hiragana primarily used for?", "options": ["Foreign words", "Native Japanese words and grammar", "Chinese origin words", "Technical terms"], "correct": 1},
            {"q": "Which Hiragana represents 'a'?", "options": ["い", "う", "あ", "え"], "correct": 2},
            {"q": "What are the three Japanese writing systems?", "options": ["Hiragana, Katakana, Romaji", "Hiragana, Katakana, Kanji", "Hiragana, Kanji, Romaji", "Katakana, Kanji, English"], "correct": 1},
            {"q": "What does 'ありがとう' (arigatou) mean?", "options": ["Hello", "Goodbye", "Please", "Thank you"], "correct": 3},
        ],
    },

    # MUSIC (4)
    {
        "title": "Guitar for Absolute Beginners",
        "category": "MUSIC",
        "level": "BEGINNER",
        "price": 0,
        "offersCertificate": False,
        "description": "Pick up the guitar and start playing your favorite songs from your very first lesson. Learn proper posture, basic chords, strumming patterns, and simple melodies. This course gives you a fun and structured path to becoming a confident guitar player.",
        "topic": "Guitar",
        "questions": [
            {"q": "How many strings does a standard guitar have?", "options": ["4", "5", "6", "7"], "correct": 2},
            {"q": "What is a chord?", "options": ["A single note", "A group of notes played together", "A guitar string", "A strumming pattern"], "correct": 1},
            {"q": "What does a guitar capo do?", "options": ["Tunes the guitar", "Raises the pitch by clamping across the frets", "Mutes strings", "Adds effects"], "correct": 1},
            {"q": "What is standard guitar tuning (low to high)?", "options": ["EADGBE", "GDAEBC", "EABGDA", "ADGCEA"], "correct": 0},
            {"q": "What is fingerpicking?", "options": ["Chord strumming", "Playing individual strings with fingers instead of a pick", "A guitar style only for classical music", "A warm-up exercise"], "correct": 1},
        ],
    },
    {
        "title": "Music Theory Fundamentals",
        "category": "MUSIC",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Understand the language of music including notes, scales, intervals, chords, rhythm, and keys. Learn to read sheet music, understand chord progressions, and analyze your favorite songs. Music theory makes you a better musician and composer regardless of instrument.",
        "topic": "Music Theory",
        "questions": [
            {"q": "How many notes are in a major scale?", "options": ["5", "7", "8", "12"], "correct": 1},
            {"q": "What is an interval?", "options": ["A musical break", "The distance between two pitches", "A chord type", "A rhythm pattern"], "correct": 1},
            {"q": "What are the notes of a C major scale?", "options": ["C D E F G A B", "C D Eb F G A Bb", "C D E F# G A B", "C Db E F G Ab B"], "correct": 0},
            {"q": "What is a chord progression?", "options": ["A scale pattern", "A sequence of chords that forms a harmonic structure", "A melody line", "A rhythm pattern"], "correct": 1},
            {"q": "What does BPM stand for?", "options": ["Bass Per Measure", "Beats Per Minute", "Bars Per Melody", "Beat Pattern Mode"], "correct": 1},
        ],
    },
    {
        "title": "Piano from Scratch",
        "category": "MUSIC",
        "level": "BEGINNER",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Learn to play piano from absolute zero with no music reading experience required. Master hand positioning, basic scales, simple pieces, and foundational technique. This course gets you playing real music quickly while building habits that last a lifetime.",
        "topic": "Piano",
        "questions": [
            {"q": "How many keys does a standard piano have?", "options": ["72", "76", "88", "96"], "correct": 2},
            {"q": "What does 'forte' mean in music?", "options": ["Soft", "Medium", "Loud", "Very soft"], "correct": 2},
            {"q": "What is middle C on the piano?", "options": ["The lowest C key", "The C nearest to the middle of the keyboard", "Any C key", "The highest C key"], "correct": 1},
            {"q": "What is a rest in sheet music?", "options": ["A pause in the melody", "A symbol indicating silence for a specific duration", "A soft passage", "A chord symbol"], "correct": 1},
            {"q": "What does legato mean?", "options": ["Play fast", "Play notes smoothly and connected", "Play staccato", "Play loudly"], "correct": 1},
        ],
    },
    {
        "title": "Music Production with FL Studio",
        "category": "MUSIC",
        "level": "INTERMEDIATE",
        "price": 34.99,
        "offersCertificate": True,
        "description": "Create professional beats, melodies, and full tracks using FL Studio. Learn the mixer, step sequencer, piano roll, sample packs, VST plugins, and mastering chain. Produce music in any genre from hip-hop to electronic and release tracks that sound professional.",
        "topic": "Music Production",
        "questions": [
            {"q": "What is a DAW?", "options": ["Digital Art Workstation", "Digital Audio Workstation", "Dynamic Audio Waveform", "Direct Audio Writer"], "correct": 1},
            {"q": "What is a MIDI?", "options": ["A sound file format", "A protocol for communicating musical performance data", "A mixing technique", "A mastering plugin"], "correct": 1},
            {"q": "What is compression in audio production?", "options": ["Reducing file size", "Reducing dynamic range by leveling loud and quiet parts", "Adding reverb", "Increasing bass"], "correct": 1},
            {"q": "What is EQ used for?", "options": ["Adding reverb", "Adjusting frequency balance of audio", "Compressing dynamics", "Adding distortion"], "correct": 1},
            {"q": "What is a VST plugin?", "options": ["A video streaming tool", "A virtual instrument or effect that runs in a DAW", "A file format", "A hardware synthesizer"], "correct": 1},
        ],
    },

    # PHOTOGRAPHY (4)
    {
        "title": "Street Photography Masterclass",
        "category": "PHOTOGRAPHY",
        "level": "INTERMEDIATE",
        "price": 29.99,
        "offersCertificate": True,
        "description": "Capture the energy and soul of urban life through candid street photography. Learn the decisive moment, camera settings for fast shooting, composition on the fly, and the ethics of photographing people. Develop your unique visual voice in this exciting genre.",
        "topic": "Street Photography",
        "questions": [
            {"q": "What is the 'decisive moment' in photography?", "options": ["The exact moment you press the shutter at peak action or expression", "The best time of day to shoot", "The moment you choose a location", "When you import photos"], "correct": 0},
            {"q": "What camera setting freezes motion on streets?", "options": ["Slow shutter speed", "Fast shutter speed (1/500s+)", "Low ISO only", "Small aperture"], "correct": 1},
            {"q": "What is zone focusing?", "options": ["Focus in post-processing", "Pre-focusing at a specific distance for quick shooting", "Autofocus mode", "Focus stacking"], "correct": 1},
            {"q": "What focal length is classic for street photography?", "options": ["300mm telephoto", "35mm or 50mm", "14mm ultra-wide", "100mm macro"], "correct": 1},
            {"q": "What is the ethics consideration in street photography?", "options": ["Always use flash", "Respecting subjects' dignity and privacy", "Never shoot in public", "Always get written consent"], "correct": 1},
        ],
    },
    {
        "title": "Portrait Photography",
        "category": "PHOTOGRAPHY",
        "level": "BEGINNER",
        "price": 14.99,
        "offersCertificate": False,
        "description": "Create stunning portraits by mastering lighting, posing, composition, and connection with your subjects. Learn natural light portrait techniques, basic studio lighting setups, and post-processing in Lightroom. Capture images that reveal the personality and emotion of your subjects.",
        "topic": "Portrait Photography",
        "questions": [
            {"q": "What aperture is best for background blur in portraits?", "options": ["f/11 or higher", "f/1.8 or f/2.8", "f/22", "f/8"], "correct": 1},
            {"q": "What is catchlight?", "options": ["A lighting modifier", "The reflection of light in a subject's eyes", "A catch-all lighting term", "A lens flare type"], "correct": 1},
            {"q": "What is the most flattering focal length for portraits?", "options": ["14-24mm", "85-135mm", "300-400mm", "50mm prime only"], "correct": 1},
            {"q": "What is a reflector used for in portrait photography?", "options": ["Blocking light", "Redirecting and filling in shadows", "Adding diffusion", "Measuring light"], "correct": 1},
            {"q": "What is the 'eyes in focus' rule?", "options": ["Never focus on eyes", "Always ensure the eyes are sharp in portraits", "Focus on the nose", "Use manual focus only"], "correct": 1},
        ],
    },
    {
        "title": "Landscape Photography",
        "category": "PHOTOGRAPHY",
        "level": "INTERMEDIATE",
        "price": 24.99,
        "offersCertificate": False,
        "description": "Photograph breathtaking landscapes by mastering the golden hour, composition rules, long exposure, and filters. Learn to plan shoots using weather apps and sun position tools, and develop your images with advanced Lightroom techniques. Chase the light and create images that inspire wonder.",
        "topic": "Landscape Photography",
        "questions": [
            {"q": "What is the golden hour?", "options": ["Noon light", "The hour after sunrise and before sunset", "Overcast daylight", "Blue hour at night"], "correct": 1},
            {"q": "What is a ND filter used for?", "options": ["Adding warmth", "Reducing light to allow long exposures in bright conditions", "Adding polarization", "Reducing lens flare"], "correct": 1},
            {"q": "What is the rule of thirds?", "options": ["Only three colors in a photo", "Placing key elements along grid lines dividing image in thirds", "Three exposure settings", "Three composition styles"], "correct": 1},
            {"q": "What aperture maximizes depth of field?", "options": ["f/1.8", "f/4", "f/11-f/16", "f/2.8"], "correct": 2},
            {"q": "What is focus stacking?", "options": ["Using multiple focus points", "Combining multiple images with different focus points for full sharpness", "A single sharp focus technique", "Tracking focus on moving subjects"], "correct": 1},
        ],
    },
    {
        "title": "Photo Editing with Lightroom",
        "category": "PHOTOGRAPHY",
        "level": "BEGINNER",
        "price": 19.99,
        "offersCertificate": False,
        "description": "Transform your photos from good to stunning using Adobe Lightroom Classic and Lightroom CC. Learn the full editing workflow from import to export including exposure, color grading, retouching, and presets. Make every photo look its absolute best.",
        "topic": "Lightroom Photo Editing",
        "questions": [
            {"q": "What does 'white balance' control?", "options": ["Brightness", "The warmth/coolness of colors in an image", "Sharpness", "Noise reduction"], "correct": 1},
            {"q": "What does increasing 'exposure' do?", "options": ["Adds contrast", "Makes the image brighter or darker", "Changes color", "Reduces noise"], "correct": 1},
            {"q": "What is a Lightroom preset?", "options": ["A camera setting", "A saved set of editing adjustments applied in one click", "A file format", "An export setting"], "correct": 1},
            {"q": "What tool is used to remove spots in Lightroom?", "options": ["Crop tool", "Spot Removal tool", "Gradient filter", "HSL panel"], "correct": 1},
            {"q": "What does the histogram show?", "options": ["Color temperature", "Distribution of tones from shadows to highlights", "Sharpness level", "Noise level"], "correct": 1},
        ],
    },
]

# ── Comments for reviews ──────────────────────────────────────────────────────

REVIEW_COMMENTS_BY_CAT = {
    "PROGRAMMING": [
        "Excellent course! The code examples are clear and easy to follow.",
        "Great for beginners, well structured and very practical.",
        "The instructor explains complex concepts in a simple way.",
        "Very hands-on, I built real projects by the end.",
        "Perfect pacing and great quizzes to reinforce learning.",
    ],
    "DESIGN": [
        "Amazing design course, the visual examples are top notch.",
        "Helped me understand design principles I was missing.",
        "Very practical, I applied the skills immediately.",
        "Great instructor with a keen eye for detail.",
        "The project-based approach is exactly what I needed.",
    ],
    "BUSINESS": [
        "Clear and practical business insights I could use right away.",
        "Very well structured, covers everything you need to know.",
        "The instructor's industry experience really shows.",
        "Great course with actionable frameworks.",
        "Loved the real-world case studies throughout.",
    ],
    "MARKETING": [
        "This course transformed my marketing approach.",
        "Very practical and results-focused, loved every lesson.",
        "The strategies taught here actually work.",
        "Great value for anyone starting in digital marketing.",
        "Clear explanations with real campaign examples.",
    ],
    "SCIENCE": [
        "Finally a science course that makes complex topics accessible.",
        "Well explained with clear examples and visuals.",
        "The instructor has a real passion for the subject.",
        "Very thorough coverage, I learned so much.",
        "Engaging and educational, highly recommend.",
    ],
    "MATH": [
        "Best math course I've taken — finally makes sense!",
        "Clear step-by-step explanations, no confusion.",
        "The practice problems really reinforced the concepts.",
        "I was struggling before this course, now I feel confident.",
        "Excellent pacing and very comprehensive.",
    ],
    "LANGUAGE": [
        "I can already have basic conversations after this course!",
        "Great pronunciation guides and cultural insights.",
        "Very engaging approach to language learning.",
        "The structured lessons make progress feel natural.",
        "Excellent course for absolute beginners.",
    ],
    "MUSIC": [
        "This course made me fall in love with music even more.",
        "Very clear and progressive, great for beginners.",
        "I was playing songs within a week of starting.",
        "The theory sections are explained so clearly.",
        "Fantastic course, exactly what I was looking for.",
    ],
    "PHOTOGRAPHY": [
        "My photos have improved dramatically after this course.",
        "Very practical tips that make an immediate difference.",
        "Great balance of technical knowledge and creative guidance.",
        "The editing workflow alone is worth the course price.",
        "Inspiring and highly educational.",
    ],
}

# ── Helpers ───────────────────────────────────────────────────────────────────

def create_course(course_data, idx):
    """Create a course using multipart/form-data."""
    import io
    # Always set offersCertificate=False to avoid requiring a certificate file upload
    course_json = {
        "title": course_data["title"],
        "description": course_data["description"],
        "price": course_data["price"],
        "category": course_data["category"],
        "level": course_data["level"],
        "approximateDurationMinutes": 180,
        "isPublished": True,
        "offersCertificate": False,
        "instructorId": 1,
    }
    json_bytes = json.dumps(course_json).encode("utf-8")
    files = {
        "course": ("course.json", io.BytesIO(json_bytes), "application/json"),
    }
    resp = requests.post(f"{COURSE_BASE}/api/courses", files=files, timeout=30)
    return resp


def create_lesson(course_id, title, description, order_index, duration, youtube_url):
    """Create a lesson using multipart/form-data."""
    import io
    lesson_json = {
        "title": title,
        "description": description,
        "orderIndex": order_index,
        "durationMinutes": duration,
    }
    json_bytes = json.dumps(lesson_json).encode("utf-8")
    files = {
        "lesson": ("lesson.json", io.BytesIO(json_bytes), "application/json"),
    }
    params = {"youtubeUrls": youtube_url}
    resp = requests.post(
        f"{COURSE_BASE}/api/courses/{course_id}/lessons",
        files=files,
        params=params,
        timeout=30,
    )
    return resp


def create_quiz(course_id, title):
    quiz_body = {
        "title": title,
        "description": f"Assessment for {title}",
        "timeLimitMinutes": 20,
        "passingScore": 70,
        "maxAttempts": 3,
    }
    resp = requests.post(
        f"{QUIZ_BASE}/api/quizzes",
        params={"courseId": course_id},
        json=quiz_body,
        timeout=30,
    )
    return resp


def add_question(quiz_id, question_text, options_list, correct_idx):
    options = [
        {"optionText": opt, "isCorrect": (i == correct_idx)}
        for i, opt in enumerate(options_list)
    ]
    body = {"questionText": question_text, "options": options}
    resp = requests.post(
        f"{QUIZ_BASE}/api/quizzes/{quiz_id}/questions",
        json=body,
        timeout=30,
    )
    return resp


def enroll_student(student_id, course_id):
    resp = requests.post(
        f"{COURSE_BASE}/api/enrollments",
        json={"studentId": student_id, "courseId": course_id},
        timeout=30,
    )
    return resp


def add_review(course_id, student_id, rating, comment):
    resp = requests.post(
        f"{COURSE_BASE}/api/courses/{course_id}/reviews",
        json={"studentId": student_id, "rating": rating, "comment": comment},
        timeout=30,
    )
    return resp


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print(f"Seeding {len(COURSES)} courses to YBrainy...")
    print(f"  Course service: {COURSE_BASE}")
    print(f"  Quiz service:   {QUIZ_BASE}")
    print()

    created_course_ids = []
    created_course_categories = []

    for idx, course_data in enumerate(COURSES):
        title = course_data["title"]
        cat   = course_data["category"]
        print(f"[{idx+1:02d}/{len(COURSES)}] Creating course: {title} ({cat})")

        # 1. Create course
        resp = create_course(course_data, idx)
        if resp.status_code not in (200, 201):
            print(f"  ERROR creating course: {resp.status_code} {resp.text[:200]}")
            continue

        course_id = resp.json().get("id") or resp.json().get("courseId")
        if not course_id:
            print(f"  ERROR: no id in response: {resp.text[:200]}")
            continue

        created_course_ids.append(course_id)
        created_course_categories.append(cat)
        print(f"  Course ID: {course_id}")

        # 2. Create 4 lessons
        topic = course_data.get("topic", title)
        lessons = [
            (f"Introduction to {topic}", f"Get a solid overview of {topic}, understand its core concepts and why it matters in today's world. We set clear goals for what you will learn.", 0, 35),
            (f"Core Concepts of {topic}", f"Dive into the fundamental building blocks of {topic}. Through clear explanations and worked examples we build your understanding layer by layer.", 1, 55),
            (f"Practical {topic} — Hands On", f"Apply what you have learned in hands-on exercises and real-world projects. This lesson is where theory becomes practice and skills are truly built.", 2, 75),
            (f"Advanced {topic} Techniques", f"Level up with advanced techniques, patterns, and best practices used by professionals. We explore edge cases and strategies for mastery.", 3, 60),
        ]
        for l_idx, (l_title, l_desc, l_order, l_dur) in enumerate(lessons):
            yt_url = YOUTUBE_URLS[l_idx % len(YOUTUBE_URLS)]
            l_resp = create_lesson(course_id, l_title, l_desc, l_order, l_dur, yt_url)
            if l_resp.status_code not in (200, 201):
                print(f"  WARN lesson {l_idx+1}: {l_resp.status_code} {l_resp.text[:100]}")
            else:
                print(f"  Lesson {l_idx+1} created")

        # 3. Create quiz
        quiz_title = f"{title} Assessment"
        q_resp = create_quiz(course_id, quiz_title)
        if q_resp.status_code not in (200, 201):
            print(f"  WARN quiz: {q_resp.status_code} {q_resp.text[:200]}")
        else:
            quiz_id = q_resp.json().get("id") or q_resp.json().get("quizId")
            print(f"  Quiz ID: {quiz_id}")

            # 4. Add 5 questions
            for q_def in course_data.get("questions", []):
                qr = add_question(quiz_id, q_def["q"], q_def["options"], q_def["correct"])
                if qr.status_code not in (200, 201):
                    print(f"  WARN question: {qr.status_code} {qr.text[:100]}")

            print(f"  {len(course_data.get('questions', []))} questions added")

        print()

    print(f"\nCreated {len(created_course_ids)} courses total.")

    # ── Enrollments ──────────────────────────────────────────────────────────
    print("\nCreating enrollments...")
    # Student 1 and 2 enroll in 15 courses each, spread across categories
    # Pick every 5th course for student 1, every 5th+2 for student 2
    enroll_ids_1 = created_course_ids[0::5][:15]
    enroll_ids_2 = created_course_ids[2::5][:15]

    enrolled_1 = []
    for cid in enroll_ids_1:
        er = enroll_student(1, cid)
        if er.status_code in (200, 201):
            enrolled_1.append(cid)
        elif er.status_code == 409:
            enrolled_1.append(cid)  # already enrolled
        else:
            print(f"  WARN enroll student 1 course {cid}: {er.status_code}")

    enrolled_2 = []
    for cid in enroll_ids_2:
        er = enroll_student(2, cid)
        if er.status_code in (200, 201):
            enrolled_2.append(cid)
        elif er.status_code == 409:
            enrolled_2.append(cid)
        else:
            print(f"  WARN enroll student 2 course {cid}: {er.status_code}")

    print(f"  Student 1 enrolled in {len(enrolled_1)} courses")
    print(f"  Student 2 enrolled in {len(enrolled_2)} courses")

    # ── Reviews ──────────────────────────────────────────────────────────────
    print("\nAdding reviews...")
    import random
    random.seed(42)

    review_count = 0
    # Add reviews to the first 40 courses (spread nicely)
    review_targets = created_course_ids[:40]
    for i, cid in enumerate(review_targets):
        cat = created_course_categories[i] if i < len(created_course_categories) else "PROGRAMMING"
        comments = REVIEW_COMMENTS_BY_CAT.get(cat, REVIEW_COMMENTS_BY_CAT["PROGRAMMING"])
        n_reviews = random.randint(2, 4)
        used_students = []
        for r_idx in range(n_reviews):
            student_id = (r_idx % 2) + 1  # alternate student 1 and 2
            if student_id in used_students:
                continue
            used_students.append(student_id)
            rating = random.randint(3, 5)
            comment = comments[r_idx % len(comments)]
            rr = add_review(cid, student_id, rating, comment)
            if rr.status_code in (200, 201):
                review_count += 1
            elif rr.status_code == 409:
                pass  # already reviewed
            # else: silently skip

    print(f"  Added {review_count} reviews")

    # ── Final summary ─────────────────────────────────────────────────────────
    print("\n" + "="*60)
    print("SEEDING COMPLETE")
    print("="*60)
    print(f"Courses created:  {len(created_course_ids)}")
    print(f"Enrollments:      student 1: {len(enrolled_1)}, student 2: {len(enrolled_2)}")
    print(f"Reviews added:    {review_count}")

    # Category breakdown
    from collections import Counter
    cat_counts = Counter(created_course_categories)
    print("\nCourses by category:")
    for cat, count in sorted(cat_counts.items()):
        print(f"  {cat}: {count}")


if __name__ == "__main__":
    main()
