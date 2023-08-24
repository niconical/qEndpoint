#!/usr/bin/env bash
INDEX_HDT=/app/qendpoint/hdt-store/offline/index_dev.hdt
INDEX_HDT_COINDEX=$INDEX_HDT.index.v1-1

HDT="$CDN/$HDT_BASE.hdt"

echo "Search for $HDT_BASE index at $HDT"

if [ -f "$INDEX_HDT" ]; then
    echo "$INDEX_HDT exists."
fi

if [ -f "$INDEX_HDT_COINDEX" ]; then
    echo "$INDEX_HDT_COINDEX exists."
fi

# offline data
mv $INDEX_HDT ..
mv $INDEX_HDT_COINDEX ..
rm -rf /app/qendpoint/hdt-store/offline