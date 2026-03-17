#!/usr/bin/env python3
"""
Test script to verify mapping file discovery in the directory structure.
"""

import sys
from pathlib import Path
from collections import Counter

def find_mapping_files(base_path):
    """Find all mapping files in the directory structure"""
    base_path = Path(base_path)
    mapping_files = []
    org_counts = Counter()
    collection_counts = Counter()
    
    print(f"Scanning directory: {base_path}")
    print("=" * 60)
    
    # Look for org directories
    for org_dir in base_path.iterdir():
        if not org_dir.is_dir():
            continue
            
        org_name = org_dir.name
        
        # Look for PocketMapper/work directories
        for work_dir in org_dir.glob('PocketMapper/work*'):
            if not work_dir.is_dir():
                continue
                
            work_type = work_dir.name  # work, work2, work_test etc
            
            # Each subdirectory is a collection
            for collection_dir in work_dir.iterdir():
                if not collection_dir.is_dir() or collection_dir.name.startswith('__'):
                    continue
                
                # Skip .sip.zip and output directories
                if collection_dir.name.endswith('.sip.zip') or collection_dir.name == 'output':
                    continue
                
                # Get all mapping files in this collection
                collection_mappings = []
                for xml_file in collection_dir.glob('*mapping*.xml'):
                    # Skip if in subdirectory
                    if xml_file.parent == collection_dir:
                        collection_mappings.append(xml_file)
                        mapping_files.append(xml_file)
                
                if collection_mappings:
                    org_counts[org_name] += len(collection_mappings)
                    collection_counts[collection_dir.name] += len(collection_mappings)
                    
                    # Print first few examples
                    if len(mapping_files) <= 10:
                        print(f"\n{org_name}/PocketMapper/{work_type}/{collection_dir.name}/")
                        for mf in collection_mappings[:3]:
                            print(f"  - {mf.name}")
                        if len(collection_mappings) > 3:
                            print(f"  ... and {len(collection_mappings) - 3} more")
    
    print("\n" + "=" * 60)
    print(f"SUMMARY:")
    print(f"Total mapping files found: {len(mapping_files)}")
    print(f"\nBy organization:")
    for org, count in org_counts.most_common():
        print(f"  {org}: {count} files")
    
    print(f"\nTop collections by file count:")
    for collection, count in collection_counts.most_common(10):
        print(f"  {collection}: {count} files")
    
    return mapping_files


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python test_find_mappings.py <base_directory>")
        print("Example: python test_find_mappings.py ~/PocketMapper/lila")
        sys.exit(1)
    
    base_dir = sys.argv[1]
    mapping_files = find_mapping_files(base_dir)
    
    print(f"\nFile name patterns found:")
    patterns = Counter()
    for mf in mapping_files:
        if mf.name == 'mapping.xml':
            patterns['mapping.xml'] += 1
        elif 'mapping_' in mf.name:
            patterns['mapping_*.xml'] += 1
        else:
            patterns['other'] += 1
    
    for pattern, count in patterns.items():
        print(f"  {pattern}: {count}")