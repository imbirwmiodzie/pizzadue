# Build from the repository root (the server shares shared/ with the app):
#   docker build -f server/Dockerfile -t pizzaioli-server .
FROM gradle:8.11.1-jdk17 AS build
WORKDIR /src
COPY shared shared
COPY server server
RUN gradle --no-daemon -p server installDist

FROM eclipse-temurin:17-jre
RUN useradd --system --home /app pizza && mkdir -p /data && chown pizza /data
COPY --from=build /src/server/build/install/pizzaioli-server /app
USER pizza
ENV DATA_DIR=/data PORT=8080
EXPOSE 8080
VOLUME /data
CMD ["/app/bin/pizzaioli-server"]
