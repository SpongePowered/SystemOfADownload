#
# This file is part of SystemOfADownload, licensed under the MIT License (MIT).
#
# Copyright (c) SpongePowered <https://spongepowered.org/>
# Copyright (c) contributors
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

FROM openjdk:16-alpine3.13

EXPOSE 8080 8558 2552

COPY --chown=1337:1337 deps-release /etc/lagom/lib/
COPY --chown=1337:1337 deps-snapshot /etc/lagom/lib/
COPY --chown=1337:1337 maven /etc/lagom/app/
COPY --chown=1337:1337 config /etc/lagom/config/

RUN \
apk --no-cache --update add dumb-init && \
rm -rf /var/cache/apk/* /tmp && \
mkdir /tmp && \
chmod 777 /tmp

ENV CLASS_PATH="/etc/lagom/app:/etc/lagom/lib"


RUN export classpath=${CLASSPATH};echo $classpath;

ENV CLASS_PATH="$CLASSPATH"

ENV JAVA_OPTS=""


RUN mkdir -p /etc/lagom/app
RUN chown -R 1337:1337 /etc/lagom/app
RUN chmod 644 /etc/lagom/lib
RUN chmod 644 /etc/lagom/config

WORKDIR /etc/lagom

USER 1337

CMD /usr/bin/dumb-init java $JAVA_OPTS --illegal-access=permit -cp $CLASS_PATH play.core.server.ProdServerStart

