#!/bin/bash
# Portable, self-contained Bash script for starting FOLIO services and dropping tenants.
# (Corrected: ensure console output files are written but not shown on main terminal;
#  ensure -Dgrails.env is passed properly; backgrounding fixed.)

## !WARNING! This script is intended for developer testing ONLY and was largely written with the aid of AI so is NOT to be trusted at face value.

# RUN INSTRUCTIONS
# Spin up the docker compose file from mod-agreements/tools/testing
# Script MUST be run from service directory mod-agreements/service
# include functions in source:
#     source ../path/to/script/start_folios_workflow.sh

# The script is now ready to be run by calling the main function:
#     terminals_start_folios_portable 2 2 'DB_MAXPOOLSIZE=15'



# ==============================================================================
# 1. CORE DEPENDENCY FUNCTIONS
# ==============================================================================

# General Grails Start Function (Used by grails_start_dc)
function _grails_start {
    local GRAILS_ENV="$1"
    local GRAILS_PORT="$2"
    local CONSOLEOUTPUTFILENAME="${3:-ConsoleOutput}"
    local SERVICE_DIR="$4"

    if [ -z "$GRAILS_ENV" ]; then
        echo "Error: grails_start requires the grails.env as the first argument." >&2
        return 1
    fi

    if [ -n "$SERVICE_DIR" ]; then
        cd "${SERVICE_DIR}" || { echo "Error: Failed to cd to ${SERVICE_DIR}" >&2; exit 1; }
    fi

    local COMMAND="./gradlew -Dgrails.env=${GRAILS_ENV}"
    if [ -n "$GRAILS_PORT" ]; then
        COMMAND="${COMMAND} -Dgrails.server.port=${GRAILS_PORT}"
    fi
    COMMAND="${COMMAND} bootRun"

    echo "Starting Grails with command: ${COMMAND}"

    # Add this: redirect output directly here if CONSOLEOUTPUTFILENAME is set
    if [ -n "${CONSOLEOUTPUTFILENAME}" ]; then
        local LOGFILE="$(pwd)/${CONSOLEOUTPUTFILENAME}.txt"
        echo "Logging output to ${LOGFILE}"
        # Ensure detached redirection, avoid terminal bleed
        exec > "${LOGFILE}" 2>&1
    fi

    # Execute gradlew directly (no eval, no quotes around args)
    ./gradlew -Dgrails.env="${GRAILS_ENV}" ${GRAILS_PORT:+-Dgrails.server.port=${GRAILS_PORT}} bootRun
}


# Grails Start Alias: pass environment 'dc' and forward args
function _grails_start_dc {
    _grails_start dc "$@"
}

# ==============================================================================
# 2. TENANT MANAGEMENT FUNCTIONS
# ==============================================================================

function _drop_tenant_function {
    local PORT="$1"
    local TENANT_PARAM="$2"
    local OKAPI_BASE_URL="http://localhost:${PORT}/_/tenant"

    if [ -z "$PORT" ] || [ -z "$TENANT_PARAM" ]; then
        echo "Error: _drop_tenant_function requires port and tenant ID." >&2
        return 1
    fi

    if [ -f .okapirc ]; then
        . .okapirc
    elif [ -f "$HOME/.okapirc" ]; then
        . "$HOME/.okapirc"
    else
        echo "Warning: .okapirc not found. Assuming environment variables for Okapi/Supertenant are already set."
    fi
    
    echo "  -> Attempting to drop and re-initialize tenant \"${TENANT_PARAM}\" on port ${PORT}"

    echo -n "    -> Deleting tenant (HTTP Code: "
    curl -s -o /dev/null -w "%{http_code}" -H "X-OKAPI-TENANT: ${TENANT_PARAM}" -XDELETE "${OKAPI_BASE_URL}"
    echo ")"

    echo -n "    -> Initializing tenant (HTTP Code: "
    curl -s -o /dev/null -w "%{http_code}" -XPOST -H 'Content-Type: application/json' -H "X-OKAPI-TENANT: ${TENANT_PARAM}" "${OKAPI_BASE_URL}" -d '{ "parameters": [{"key": "loadSample", "value": "test"}, {"key": "loadReference", "value": "other"}]}'
    echo ")"
    
    return 0
}

function _multiple_drop_tenants_function {
    local END_TENANT="${1}"
    local START_TENANT="${2}"
    local PORT="${3}"
    local TENANT_PREFIX="test"

    if [ -z "$END_TENANT" ] || [ -z "$START_TENANT" ] || [ -z "$PORT" ]; then
        echo "Error: _multiple_drop_tenants_function requires END_TENANT, START_TENANT, and PORT." >&2
        return 1
    fi

    local TENANT_COUNT=$((END_TENANT - START_TENANT + 1))
    echo "Processing ${TENANT_COUNT} tenants: ${TENANT_PREFIX}${START_TENANT} to ${TENANT_PREFIX}${END_TENANT} on port ${PORT}."

    for (( i=START_TENANT; i<=END_TENANT; i++ )); do
        local TENANT_ID="${TENANT_PREFIX}${i}"
        _drop_tenant_function "$PORT" "$TENANT_ID"
        if [ $? -ne 0 ]; then
            echo "Fatal error during tenant drop for ${TENANT_ID}." >&2
            return 1
        fi
    done
    return 0
}

# ==============================================================================
# 3. HELPER FUNCTIONS
# ==============================================================================

_check_service_port_ready() {
    local CURRENT_PORT=$1
    local SERVICE_DIR=$2
    local READY_STRING="Grails application running at"
    local OUTPUT_FILE="${SERVICE_DIR}/ConsoleOutput${CURRENT_PORT}.txt"

    if [ -f "${OUTPUT_FILE}" ] && grep -q "${READY_STRING}" "${OUTPUT_FILE}"; then
        return 0
    else
        return 1
    fi
}

_wait_for_service_readiness() {
    local N=$1
    local START_PORT=$2
    local TIMEOUT_SECONDS=${3:-300}
    local POLL_INTERVAL=${4:-5}
    local SERVICE_DIR=$5
    local START_TIME=$(date +%s)
    local ALL_READY=0

    echo -e "\n--- WAITING FOR ${N} SERVICE INSTANCES TO BE READY (Timeout: ${TIMEOUT_SECONDS}s) ---"

    while [ "$ALL_READY" -lt "$N" ] && [ "$(($(date +%s) - START_TIME))" -lt "$TIMEOUT_SECONDS" ]; do
        local CURRENT_READY=0
        
        for (( i=0; i<N; i++ )); do
            local CURRENT_PORT=$((START_PORT + i))
            _check_service_port_ready "${CURRENT_PORT}" "${SERVICE_DIR}"
            if [ $? -eq 0 ]; then
                CURRENT_READY=$((CURRENT_READY + 1))
            fi
        done

        if [ "$CURRENT_READY" -gt "$ALL_READY" ]; then
            echo "-> ${CURRENT_READY}/${N} services are now ready."
            ALL_READY="$CURRENT_READY"
        fi

        if [ "$ALL_READY" -lt "$N" ]; then
            echo "Waiting for remaining services (${ALL_READY}/${N}). Retrying in ${POLL_INTERVAL}s..."
            sleep "$POLL_INTERVAL"
        fi
    done

    if [ "$ALL_READY" -eq "$N" ]; then
        echo -e "\n*** SUCCESS: All ${N} service instances are ready! ***\n"
        return 0
    else
        echo -e "\n*** FAILURE: Timeout reached. Only ${ALL_READY}/${N} services became ready. Continuing anyway. ***\n"
        return 1
    fi
}

# ==============================================================================
# 4. WORKFLOW PART 1: LAUNCH SERVICES (fixed backgrounding + redirection)
# ==============================================================================

_launch_folios_portable() {
    local N=$1
    local ENV_VARS="$2"
    local START_PORT=$3
    local SERVICE_DIR=$4

    echo "Launching $N FOLIO mod-agreements instances starting from port $START_PORT in the background..."

    if [ -n "${ENV_VARS}" ]; then
        echo "Note: Applying environment variables: ${ENV_VARS}"
    fi

    local PIDS=()

    for (( i=0; i<N; i++ )); do
        local CURRENT_PORT=$((START_PORT + i))
        local OUTPUT_FILE="ConsoleOutput${CURRENT_PORT}.txt"
        local OUTPUT_PATH="${SERVICE_DIR}/${OUTPUT_FILE}"

        # Build the startup command
        local CMD="
            source ${BASH_SOURCE[0]};
            export ${ENV_VARS}
            _grails_start_dc "${CURRENT_PORT}" "ConsoleOutput${CURRENT_PORT}" "${SERVICE_DIR}"
        "

        # Run detached, logging to OUTPUT_PATH
        nohup bash -c "${CMD}" > "${OUTPUT_PATH}" 2>&1 < /dev/null &

        local SUB_PID=$!
        PIDS+=("${SUB_PID}")
        echo "-> Launched instance $i on port ${CURRENT_PORT} (PID: ${SUB_PID}), log: ${OUTPUT_PATH}"
        sleep 0.1
    done

    LAUNCHED_SERVICE_PIDS=("${PIDS[@]}")
}



# ==============================================================================
# 5. WORKFLOW PART 2: DROP TENANTS (Parallelized)
# ==============================================================================

_drop_tenants_portable() {
    local TOTAL_TENANT_COUNT=$1
    local NUM_INSTANCES=$2
    local START_PORT=$3
    local TENANT_ID_OFFSET=${4:-1}

    if [ "${TOTAL_TENANT_COUNT}" -eq 0 ] || [ "${NUM_INSTANCES}" -eq 0 ]; then
        echo "Warning: Total tenant count and number of instances must be greater than zero. Skipping tenant drop."
        return 0
    fi
    
    local OVERALL_END_TENANT=$((TENANT_ID_OFFSET + TOTAL_TENANT_COUNT - 1))
    local TENANTS_PER_INSTANCE=$(( (TOTAL_TENANT_COUNT + NUM_INSTANCES - 1) / NUM_INSTANCES ))
    
    echo -e "\n--- LAUNCHING TENANT DROP SCRIPTS IN PARALLEL ---"
    echo "Distributing ${TOTAL_TENANT_COUNT} tenants (IDs ${TENANT_ID_OFFSET} to ${OVERALL_END_TENANT}) across ${NUM_INSTANCES} virtual instances (approx. ${TENANTS_PER_INSTANCE} per instance), starting from port ${START_PORT}."
    
    local CURRENT_TENANT_ID="${TENANT_ID_OFFSET}"
    local PIDS=()
    
    for (( i=0; i<NUM_INSTANCES; i++ )); do
        local CURRENT_PORT=$((START_PORT + i))
        local BATCH_END_ID=$((CURRENT_TENANT_ID + TENANTS_PER_INSTANCE - 1))
        
        if [ "$BATCH_END_ID" -gt "$OVERALL_END_TENANT" ]; then
            BATCH_END_ID="$OVERALL_END_TENANT"
        fi
        
        if [ "$CURRENT_TENANT_ID" -gt "$OVERALL_END_TENANT" ]; then
            echo "Finished assigning all tenants. Stopping at virtual instance $i."
            break
        fi

        echo -e "\n* Launching batch $((i + 1)) in background: Tenants ${CURRENT_TENANT_ID} to ${BATCH_END_ID} using port ${CURRENT_PORT} *"
        
        _multiple_drop_tenants_function "${BATCH_END_ID}" "${CURRENT_TENANT_ID}" "${CURRENT_PORT}" &
        PIDS+=($!)
        echo "-> Tenant drop job started (PID: $!)."
        
        CURRENT_TENANT_ID=$((BATCH_END_ID + 1))
        sleep 0.1
    done

    echo -e "\n--- WAITING FOR ${#PIDS[@]} TENANT DROP JOB(S) TO COMPLETE ---"
    local ALL_SUCCESS=0
    
    for pid in "${PIDS[@]}"; do
        wait "$pid"
        if [ $? -ne 0 ]; then
             echo "ERROR: Tenant drop process (PID: $pid) failed."
             ALL_SUCCESS=1
        fi
    done
    
    if [ "$ALL_SUCCESS" -eq 0 ]; then
        echo "*** SUCCESS: All tenant drop jobs completed successfully. ***"
    else
        echo "*** FAILURE: One or more tenant drop jobs failed. Check logs for details. ***"
        return 1
    fi
    
    return 0
}

# ==============================================================================
# 6. MAIN WORKFLOW FUNCTION
# ==============================================================================

function terminals_start_folios_portable() {
    local DEFAULT_SERVICE_DIR="${HOME}/FolioModules/ERM_Backend/mod-agreements/service"

    if [ -z "$1" ] || ! [[ "$1" =~ ^[0-9]+$ ]] || \
       [ -z "$2" ] || ! [[ "$2" =~ ^[0-9]+$ ]]; then
        echo "Error: Please provide the Number of Instances (N) and Total Tenants (T)."
        echo "Usage: terminals_start_folios_portable <N> <T> [ENV_VARS] [base_port] [TIMEOUT] [POLL_INTERVAL] [SERVICE_DIR]"
        echo "Example: terminals_start_folios_portable 8 40 'export DB_MAXPOOLSIZE=15;' 8080"
        return 1
    fi

    local NUM_INSTANCES=$1
    local TOTAL_TENANTS=$2
    local ENV_VARS="$3"
    local START_PORT=${4:-8080}
    local TIMEOUT_SECONDS="$5"
    local POLL_INTERVAL="$6"
    local SERVICE_DIR=${7:-$DEFAULT_SERVICE_DIR}

    if [ ! -d "${SERVICE_DIR}" ]; then
        echo "Error: Service directory not found at: ${SERVICE_DIR}"
        echo "Please verify the path or provide it as the 7th argument."
        return 1
    fi

    echo "--- STARTING PORTABLE FOLIO WORKFLOW ---"
    echo "Instances: ${NUM_INSTANCES}, Total Tenants: ${TOTAL_TENANTS}, Base Port: ${START_PORT}"
    echo "Service Directory: ${SERVICE_DIR}"
    
    # 1. Launch Services in the background
    _launch_folios_portable "${NUM_INSTANCES}" "${ENV_VARS}" "${START_PORT}" "${SERVICE_DIR}"

    sleep 1 # small pause to let processes spin up

    # 2. Wait for Services to be Ready
    _wait_for_service_readiness "${NUM_INSTANCES}" "${START_PORT}" "${TIMEOUT_SECONDS}" "${POLL_INTERVAL}" "${SERVICE_DIR}"

    # 3. Launch Tenant Drop Scripts in parallel
    _drop_tenants_portable "${TOTAL_TENANTS}" "${NUM_INSTANCES}" "${START_PORT}"

    echo "--- PORTABLE FOLIO WORKFLOW COMPLETE ---"
    echo "Service instances are running in the background. Use 'jobs' or 'ps -f' to inspect them."
    echo "To stop the background services, run 'kill <PID>' for each PID shown above, or: killall java"
}

# End of script
