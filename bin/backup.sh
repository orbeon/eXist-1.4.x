#!/bin/bash
# -----------------------------------------------------------------------------
# backup.sh - Backup tool start script
#
# $Id$
# -----------------------------------------------------------------------------

#
# In addition to the other parameter options for the interactive client 
# pass -j or --jmx to enable JMX agent.  The port for it can be specified 
# with optional port number e.g. -j1099 or --jmx=1099.
#

case "$0" in
	/*)
		SCRIPTPATH=$(dirname "$0")
		;;
	*)
		SCRIPTPATH=$(dirname "$PWD/$0")
		;;
esac

# source common functions and settings
source "${SCRIPTPATH}"/functions.d/eXist-settings.sh
source "${SCRIPTPATH}"/functions.d/jmx-settings.sh
source "${SCRIPTPATH}"/functions.d/getopt-settings.sh

get_opts "$@";

check_exist_home "$0";

set_exist_options;

# set java options
set_client_java_options;

# enable the JMX agent? If so, concat to $JAVA_OPTIONS:
check_jmx_status;

# save LANG
set_locale_lang;

if [ "${QUIET_ENABLED}" -gt 0 ]; then
    # Be quiet, no messages on stdout
    "${JAVA_HOME}"/bin/java ${JAVA_OPTIONS} ${OPTIONS} ${DEBUG_OPTS} -jar "$EXIST_HOME/start.jar" backup "${JAVA_OPTS[@]}" > /dev/null || exit 1 # forward non-zero exit status
else
    echo "Using locale: ${LANG}";
    "${JAVA_HOME}"/bin/java ${JAVA_OPTIONS} ${OPTIONS} ${DEBUG_OPTS} -jar "$EXIST_HOME/start.jar" backup "${JAVA_OPTS[@]}" || exit 1 # forward non-zero exit status

fi

restore_locale_lang;
