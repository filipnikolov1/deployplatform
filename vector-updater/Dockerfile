FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN go build -o /out/updater .

FROM docker:27-cli
RUN apk add --no-cache docker-compose
COPY --from=build /out/updater /usr/local/bin/updater
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/updater"]
