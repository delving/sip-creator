#!/usr/bin/env python3
"""
Extract and categorize Groovy features for implementation planning.
Groups features by complexity and provides implementation priority.
"""

import os
import re
import xml.etree.ElementTree as ET
from collections import defaultdict, Counter
from pathlib import Path
import json

class GroovyFeatureExtractor:
    def __init__(self):
        self.features = {
            'basic': defaultdict(set),      # Basic features that must be implemented
            'intermediate': defaultdict(set), # Common patterns
            'advanced': defaultdict(set),     # Complex features
            'rare': defaultdict(set)         # Rarely used features
        }
        self.examples = defaultdict(list)
        self.implementation_stats = Counter()
        
    def analyze_code(self, code, context=""):
        """Analyze a code snippet and categorize features"""
        
        # === BASIC FEATURES (Must have) ===
        
        # String literals and concatenation
        if '"' in code or "'" in code:
            self.features['basic']['string_literals'].add('basic strings')
            self.examples['string_literals'].append(code[:100])
            
        # String interpolation
        interpolations = re.findall(r'\$\{([^}]+)\}', code)
        if interpolations:
            self.features['basic']['string_interpolation'].update(interpolations[:5])
            self.examples['string_interpolation'].append(code[:100])
            
        # Property access (dot notation)
        props = re.findall(r'(?:^|[^.\w])(\w+(?:\.\w+)+)', code)
        if props:
            self.features['basic']['property_access'].update(props[:5])
            self.examples['property_access'].append(code[:100])
            
        # Method calls
        methods = re.findall(r'\.(\w+)\s*\([^)]*\)', code)
        if methods:
            self.features['basic']['method_calls'].update(methods)
            self.examples['method_calls'].append(code[:100])
            
        # Basic operators
        if any(op in code for op in [' + ', ' - ', ' == ', ' != ']):
            self.features['basic']['operators'].add('arithmetic/comparison')
            
        # === INTERMEDIATE FEATURES (Common patterns) ===
        
        # Safe navigation
        if '?.' in code:
            self.features['intermediate']['safe_navigation'].add('?.')
            self.examples['safe_navigation'].append(code[:100])
            
        # Elvis operator
        if '?:' in code:
            self.features['intermediate']['elvis_operator'].add('?:')
            self.examples['elvis_operator'].append(code[:100])
            
        # List operations with 'it'
        if 'it.' in code or 'it[' in code:
            self.features['intermediate']['it_variable'].add('it usage')
            self.examples['it_variable'].append(code[:100])
            
        # Array/List access
        array_access = re.findall(r'\[([^\]]+)\]', code)
        if array_access:
            self.features['intermediate']['array_access'].update(array_access[:3])
            self.examples['array_access'].append(code[:100])
            
        # Closures
        closure_match = re.findall(r'\{([^}]*(?:->|it\.)[^}]*)\}', code)
        if closure_match:
            self.features['intermediate']['closures'].add('closure usage')
            self.examples['closures'].append(code[:100])
            
        # Loop operators
        if ' * ' in code and '{' in code:
            self.features['intermediate']['loop_operators'].add('* (map)')
            self.examples['loop_operators'].append(code[:100])
        if ' ** ' in code:
            self.features['intermediate']['loop_operators'].add('** (first)')
        if ' >> ' in code:
            self.features['intermediate']['loop_operators'].add('>> (flatten)')
        if ' | ' in code:
            self.features['advanced']['loop_operators'].add('| (zip/tuple)')
            
        # Common methods
        common_methods = ['trim', 'split', 'replace', 'toLowerCase', 'toUpperCase', 
                         'size', 'length', 'contains', 'startsWith', 'endsWith']
        for method in common_methods:
            if f'.{method}(' in code:
                self.features['intermediate']['common_methods'].add(method)
                
        # === ADVANCED FEATURES ===
        
        # Regular expressions
        regex_patterns = re.findall(r'~/([^/]+)/', code)
        if regex_patterns:
            self.features['advanced']['regex'].update(regex_patterns[:2])
            self.examples['regex'].append(code[:100])
            
        # Complex conditionals
        if re.search(r'if\s*\([^)]+\)', code) or ' ? ' in code and ' : ' in code:
            self.features['advanced']['conditionals'].add('if/ternary')
            self.examples['conditionals'].append(code[:100])
            
        # Type casting
        if ' as ' in code:
            self.features['advanced']['type_casting'].add('as operator')
            self.examples['type_casting'].append(code[:100])
            
        # Groovy collections methods
        collection_methods = ['collect', 'findAll', 'find', 'each', 'grep', 
                            'groupBy', 'sort', 'unique', 'inject', 'sum']
        for method in collection_methods:
            if f'.{method}' in code:
                self.features['advanced']['collection_methods'].add(method)
                
        # === RARE FEATURES ===
        
        # Spread operator
        if '*.' in code:
            self.features['rare']['spread_operator'].add('*.')
            self.examples['spread_operator'].append(code[:100])
            
        # Range operator
        if '..' in code and not '../' in code:  # Exclude file paths
            self.features['rare']['range_operator'].add('..')
            
        # Method references
        if '.&' in code:
            self.features['rare']['method_reference'].add('.&')
            
    def generate_implementation_guide(self):
        """Generate implementation priority guide"""
        guide = {
            'phase1_critical': {
                'description': 'Core features needed for basic functionality',
                'features': []
            },
            'phase2_common': {
                'description': 'Commonly used patterns in mappings',
                'features': []
            },
            'phase3_advanced': {
                'description': 'Advanced features for full compatibility',
                'features': []
            },
            'phase4_optional': {
                'description': 'Rarely used features - implement as needed',
                'features': []
            }
        }
        
        # Phase 1: Critical features
        for feature, values in self.features['basic'].items():
            guide['phase1_critical']['features'].append({
                'name': feature,
                'examples': list(values)[:5],
                'sample_code': self.examples.get(feature, [])[:2]
            })
            
        # Phase 2: Common patterns
        for feature, values in self.features['intermediate'].items():
            guide['phase2_common']['features'].append({
                'name': feature,
                'examples': list(values)[:5],
                'sample_code': self.examples.get(feature, [])[:2]
            })
            
        # Phase 3: Advanced features
        for feature, values in self.features['advanced'].items():
            guide['phase3_advanced']['features'].append({
                'name': feature,
                'examples': list(values)[:5],
                'sample_code': self.examples.get(feature, [])[:2]
            })
            
        # Phase 4: Optional features
        for feature, values in self.features['rare'].items():
            if values:  # Only include if actually found
                guide['phase4_optional']['features'].append({
                    'name': feature,
                    'examples': list(values)[:5],
                    'sample_code': self.examples.get(feature, [])[:2]
                })
                
        return guide
    
    def analyze_file(self, xml_file):
        """Analyze a single XML file for Groovy code"""
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            
            # Common places for Groovy code in mapping files
            for elem in root.iter():
                # Text content
                if elem.text and elem.text.strip():
                    self.analyze_code(elem.text.strip(), f"<{elem.tag}>")
                
                # Attributes (often contain expressions)
                for attr_name, attr_value in elem.attrib.items():
                    if attr_value and any(c in attr_value for c in ['$', '.', '{', '(']):
                        self.analyze_code(attr_value, f"@{attr_name}")
                        
        except Exception as e:
            print(f"Error analyzing {xml_file}: {e}")
            
    def analyze_directory(self, base_path):
        """Analyze all mapping files in directory structure"""
        base_path = Path(base_path)
        mapping_files = []
        
        # Find mapping files in org_id/PocketMapper/work/collection_name/ structure
        for org_dir in base_path.iterdir():
            if not org_dir.is_dir():
                continue
                
            # Look for PocketMapper/work directories
            for work_dir in org_dir.glob('PocketMapper/work*'):
                if not work_dir.is_dir():
                    continue
                    
                # Each subdirectory is a collection
                for collection_dir in work_dir.iterdir():
                    if not collection_dir.is_dir() or collection_dir.name.startswith('__'):
                        continue
                    
                    # Skip .sip.zip and output directories
                    if collection_dir.name.endswith('.sip.zip') or collection_dir.name == 'output':
                        continue
                    
                    # Get all mapping files in this collection
                    for xml_file in collection_dir.glob('*mapping*.xml'):
                        # Skip if in subdirectory
                        if xml_file.parent == collection_dir:
                            mapping_files.append(xml_file)
                        
        print(f"Found {len(mapping_files)} mapping files")
        
        # Analyze each file
        for i, xml_file in enumerate(mapping_files):
            if i % 10 == 0:
                print(f"Analyzing file {i+1}/{len(mapping_files)}...")
            self.analyze_file(xml_file)
            
        return len(mapping_files)
    
    def save_results(self):
        """Save analysis results"""
        # Generate implementation guide
        guide = self.generate_implementation_guide()
        
        # Save implementation guide
        with open('groovy_implementation_guide.json', 'w') as f:
            json.dump(guide, f, indent=2)
            
        # Generate markdown report
        with open('GROOVY_FEATURES_REPORT.md', 'w') as f:
            f.write("# Groovy Features Analysis Report\n\n")
            f.write("This report analyzes all Groovy code found in mapping files to determine implementation requirements.\n\n")
            
            for phase_key, phase_data in guide.items():
                phase_num = phase_key.split('_')[0].replace('phase', 'Phase ')
                f.write(f"## {phase_num}: {phase_data['description']}\n\n")
                
                for feature in phase_data['features']:
                    f.write(f"### {feature['name'].replace('_', ' ').title()}\n")
                    if feature['examples']:
                        f.write("Examples found:\n")
                        for ex in feature['examples'][:3]:
                            f.write(f"- `{ex}`\n")
                    if feature['sample_code']:
                        f.write("\nSample code:\n```groovy\n")
                        f.write(feature['sample_code'][0])
                        f.write("\n```\n")
                    f.write("\n")
                    
        print("\nResults saved to:")
        print("  - groovy_implementation_guide.json")
        print("  - GROOVY_FEATURES_REPORT.md")


if __name__ == "__main__":
    import sys
    
    base_path = sys.argv[1] if len(sys.argv) > 1 else ".."
    
    extractor = GroovyFeatureExtractor()
    num_files = extractor.analyze_directory(base_path)
    extractor.save_results()
    
    print(f"\nAnalysis complete! Analyzed {num_files} files.")