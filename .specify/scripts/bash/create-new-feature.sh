#!/bin/bash

# SpecKit Feature Creation Script
# Creates a new feature branch and initializes spec structure

set -e

# Parse arguments
JSON_OUTPUT=false
FEATURE_NUMBER=""
SHORT_NAME=""
DESCRIPTION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --json)
            JSON_OUTPUT=true
            shift
            ;;
        --number)
            FEATURE_NUMBER="$2"
            shift 2
            ;;
        --short-name)
            SHORT_NAME="$2"
            shift 2
            ;;
        *)
            DESCRIPTION="$1"
            shift
            ;;
    esac
done

# Validate inputs
if [[ -z "$FEATURE_NUMBER" ]] || [[ -z "$SHORT_NAME" ]] || [[ -z "$DESCRIPTION" ]]; then
    echo "Error: Missing required arguments"
    echo "Usage: $0 --json --number N --short-name 'name' 'description'"
    exit 1
fi

# Generate branch name
BRANCH_NAME="${FEATURE_NUMBER}-${SHORT_NAME}"
SPEC_DIR="specs/${BRANCH_NAME}"
SPEC_FILE="${SPEC_DIR}/spec.md"

# Create and checkout branch
git checkout -b "$BRANCH_NAME" 2>/dev/null || git checkout "$BRANCH_NAME"

# Create spec directory structure
mkdir -p "${SPEC_DIR}/checklists"
mkdir -p "${SPEC_DIR}/plans"
mkdir -p "${SPEC_DIR}/tasks"

# Initialize spec file with header
cat > "$SPEC_FILE" << EOF
# Feature Specification: ${DESCRIPTION}

**Status**: Draft
**Created**: $(date +%Y-%m-%d)
**Feature ID**: ${FEATURE_NUMBER}
**Branch**: ${BRANCH_NAME}

---

EOF

# Output JSON for script consumption
if [ "$JSON_OUTPUT" = true ]; then
    cat << JSON
{
  "BRANCH_NAME": "${BRANCH_NAME}",
  "FEATURE_NUMBER": ${FEATURE_NUMBER},
  "SHORT_NAME": "${SHORT_NAME}",
  "SPEC_DIR": "${SPEC_DIR}",
  "SPEC_FILE": "${SPEC_FILE}",
  "DESCRIPTION": "${DESCRIPTION}"
}
JSON
else
    echo "✓ Created branch: ${BRANCH_NAME}"
    echo "✓ Created spec directory: ${SPEC_DIR}"
    echo "✓ Initialized spec file: ${SPEC_FILE}"
fi

exit 0
