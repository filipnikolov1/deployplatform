# DockerHub Integration

Authenticated Docker image pulls from DockerHub, used by the [[Docker Service]] during the [[Deployment Pipeline]].

## Source

Auth setup in `DockerServiceImpl.java` constructor.

## How It Works

- Uses `dockerhub.username` and `dockerhub.token` (DockerHub PAT) from config
- Creates `AuthConfig` for docker-java client
- Applied to `pullImageCmd` if credentials are present
- Falls back to unauthenticated pulls if credentials are empty

## Configuration

```properties
dockerhub.username=${DOCKERHUB_USERNAME:}
dockerhub.token=${DOCKERHUB_TOKEN:}
```

Set via `.env` file — see [[Configuration]].

## DockerHub Account

Images are pushed to the `filipn123` DockerHub account by [[CI/CD Pipeline]] (GitHub Actions).

See also: [[Docker Service]], [[Docker]], [[Deployment Pipeline]]

#feature #infrastructure
