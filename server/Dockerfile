FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle ./gradle
COPY gradlew ./
COPY core ./core
COPY server ./server
RUN chmod +x gradlew && ./gradlew -p server :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 archivetune
COPY --from=build /src/server/service/build/install/server /opt/server
USER archivetune
ENV ARCHIVETUNE_PORT=8780
EXPOSE 8780
ENTRYPOINT ["/opt/server/bin/server"]
