# Database Notes

YBrainy uses several databases because each module owns its data.

## Local database strategy

The Docker Compose setup starts local database services and most Spring Boot services use:

```text
createDatabaseIfNotExist=true
```

This allows databases to be created automatically during local startup.

## Main databases

| Database | Used by |
| --- | --- |
| `ybrainy_users` | User service |
| `ybrainy_courses` | Course service |
| `ybrainy_enrollment` | Enrollment service |
| `ybrainy_quiz` | Quiz service |
| `ybrainy_payment` | Payment/cart services |
| `ybrainy_events` | Event services |
| MongoDB collections | Forum, posts, comments, messaging |

## Data policy for public submission

Runtime uploads, generated event images, local database dumps, private course files, and large AI datasets are excluded from Git.

If an evaluator needs sample data, add it as small anonymized fixtures only, or document an external public dataset in `docs/ai-models.md`.
