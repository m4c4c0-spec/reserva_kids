FROM alpine:3.21
RUN apk add --no-cache postgresql16-client gzip

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

VOLUME /backups
ENTRYPOINT ["/entrypoint.sh"]
