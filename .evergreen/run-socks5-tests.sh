#!/bin/bash

set -o xtrace   # Write all commands first to stderr
set -o errexit  # Exit the script with error if any of the commands fail

SSL=${SSL:-nossl}
MONGODB_URI=${MONGODB_URI:-}
SOCKS5_SERVER_SCRIPT="$DRIVERS_TOOLS/.evergreen/socks5srv.py"
PYTHON_BINARY=${PYTHON_BINARY:-python3}
# Grab a connection string that only refers to *one* of the hosts in MONGODB_URI
FIRST_HOST=$(echo "$MONGODB_URI" | awk -F[/:,] '{print $4":"$5}')
# Use 127.0.0.1:12345 as the URL for the single host that we connect to,
# we configure the Socks5 proxy server script to redirect from this to FIRST_HOST
export MONGODB_URI_SINGLEHOST="mongodb://127.0.0.1:12345"

if [ "${SSL}" = "ssl" ]; then
   MONGODB_URI="${MONGODB_URI}&ssl=true&sslInvalidHostNameAllowed=true"
   MONGODB_URI_SINGLEHOST="${MONGODB_URI_SINGLEHOST}/?ssl=true&sslInvalidHostNameAllowed=true"
fi

# Compute path to socks5 fake server script in a way that works on Windows
if [ "Windows_NT" == "$OS" ]; then
   SOCKS5_SERVER_SCRIPT=$(cygpath -m $DRIVERS_TOOLS)
fi

RELATIVE_DIR_PATH="$(dirname "${BASH_SOURCE:-$0}")"
. "${RELATIVE_DIR_PATH}/javaConfig.bash"

############################################
#            Functions                     #
############################################

provision_ssl () {
  # We generate the keystore and truststore on every run with the certs in the drivers-tools repo
  if [ ! -f client.pkc ]; then
    openssl pkcs12 -CAfile ${DRIVERS_TOOLS}/.evergreen/x509gen/ca.pem -export -in ${DRIVERS_TOOLS}/.evergreen/x509gen/client.pem -out client.pkc -password pass:bithere
  fi

  cp ${JAVA_HOME}/lib/security/cacerts mongo-truststore
  ${JAVA_HOME}/bin/keytool -importcert -trustcacerts -file ${DRIVERS_TOOLS}/.evergreen/x509gen/ca.pem -keystore mongo-truststore -storepass changeit -storetype JKS -noprompt

  # We add extra gradle arguments for SSL
  export GRADLE_SSL_VARS="-Pssl.enabled=true -Pssl.keyStoreType=pkcs12 -Pssl.keyStore=`pwd`/client.pkc -Pssl.keyStorePassword=bithere -Pssl.trustStoreType=jks -Pssl.trustStore=`pwd`/mongo-truststore -Pssl.trustStorePassword=changeit"
}

run_socks5_prose_tests () {
local proxyPort=$1
local authEnabled=$2
./gradlew -PjavaVersion=${JAVA_VERSION} -Dorg.mongodb.test.uri=${MONGODB_URI} \
      -Dorg.mongodb.test.uri.singleHost=${MONGODB_URI_SINGLEHOST} \
      -Dorg.mongodb.test.uri.proxyHost="127.0.0.1" \
      -Dorg.mongodb.test.uri.proxyPort=${proxyPort} \
      -Dorg.mongodb.test.uri.socks.auth.enabled=${authEnabled} \
      ${GRADLE_SSL_VARS} \
      --stacktrace --info --continue \
      driver-sync:test \
          --tests "*.Socks5ProseTest*"
}

############################################
#            Main Program                  #
############################################

# Set up keystore/truststore
if [ "${SSL}" = "ssl" ]; then
  provision_ssl
fi

# First, test with Socks5 + authentication required
echo "Running tests with Java ${JAVA_VERSION} over $SSL for $TOPOLOGY and connecting to $MONGODB_URI with socks5 auth enabled"
./gradlew -version
"$PYTHON_BINARY" "$SOCKS5_SERVER_SCRIPT" --port 1080 --auth username:p4ssw0rd --map "127.0.0.1:12345 to $FIRST_HOST" &
SOCKS5_SERVER_PID_1=$!
trap "kill $SOCKS5_SERVER_PID_1" EXIT
run_socks5_prose_tests "1080" "true"

# Second, test with Socks5 + no authentication
echo "Running tests with Java ${JAVA_VERSION} over $SSL for $TOPOLOGY and connecting to $MONGODB_URI with socks5 auth disabled"
./gradlew -version
"$PYTHON_BINARY" "$SOCKS5_SERVER_SCRIPT" --port 1081 --map "127.0.0.1:12345 to $FIRST_HOST" &
# Set up trap to kill both processes when the script exits
SOCKS5_SERVER_PID_2=$!
trap "kill $SOCKS5_SERVER_PID_1; kill $SOCKS5_SERVER_PID_2" EXIT
run_socks5_prose_tests "1081" "false"