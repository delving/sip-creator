#!/bin/bash

# Check if minimum required arguments are provided
if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <xml_file> <local_id> [node_mapping] [groovy_code_file]"
    echo "Example:"
    echo "  Regular mapping: $0 record.xml my-local-id"
    echo "  Edit path mapping: $0 record.xml my-local-id path/to/node groovy_code.groovy"
    exit 1
fi

XML_FILE=$1
LOCAL_ID=$2
NODE_MAPPING=$3
GROOVY_CODE=$4

# Function to create the base request
create_base_request() {
    local xml_content="$1"
    local id="$2"
    local node_path="$3"
    local groovy_code="$4"

    if [ -z "$node_path" ]; then
        # Basic request without edit_path
        jq -n \
            --arg xml "$xml_content" \
            --arg id "$id" \
            '{
                record_xml: $xml,
                dataset: {
                    dataset_id: "enb-304-titels",
                    workspace_id: "brabantcloud"
                },
                local_record_id: $id
            }'
    else
        # Request with edit_path included
        jq -n \
            --arg xml "$xml_content" \
            --arg id "$id" \
            --arg node "$node_path" \
            --arg code "$groovy_code" \
            '{
                record_xml: $xml,
                dataset: {
                    dataset_id: "enb-304-titels",
                    workspace_id: "brabantcloud"
                },
                local_record_id: $id,
                edit_path: {
                    node_mapping: $node,
                    groovy_code: $code,
                }
            }'
    fi
}

# Create base request with or without edit_path
BASE_REQUEST=$(create_base_request "$(cat $XML_FILE)" "$LOCAL_ID" "$NODE_MAPPING" "$GROOVY_CODE")

# If node mapping and groovy code file are provided, use edit path mapping
if [ ! -z "$NODE_MAPPING" ] && [ ! -z "$GROOVY_CODE" ]; then
    # Call edit path mapping endpoint with the base request directly
    grpcurl -plaintext -d "$BASE_REQUEST" \
        localhost:50051 mapping.v1.MappingService/MapRecord
else
    # Call regular mapping endpoint
    grpcurl -plaintext -d "$BASE_REQUEST" \
        localhost:50051 mapping.v1.MappingService/MapRecord
fi
