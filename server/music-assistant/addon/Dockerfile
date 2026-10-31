ARG BUILD_FROM
FROM $BUILD_FROM

RUN apk add --no-cache docker-cli bash curl jq

COPY archivetune/ /provider/archivetune/
COPY watcher_lib.sh /watcher_lib.sh
COPY run.sh /run.sh
RUN chmod +x /run.sh && sed -i 's/\r//' /run.sh /watcher_lib.sh

ENTRYPOINT ["/run.sh"]
