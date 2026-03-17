#!/usr/bin/env python3
"""
Analyze all mapping files to extract and categorize Groovy code patterns.
This will help identify all the Groovy features we need to implement.
"""

import os
import re
import xml.etree.ElementTree as ET
from collections import defaultdict, Counter
from pathlib import Path
import json

class MappingAnalyzer:
    def __init__(self, base_path):
        self.base_path = Path(base_path)
        self.groovy_patterns = defaultdict(list)
        self.groovy_snippets = []
        self.function_calls = Counter()
        self.operators = Counter()
        self.loop_operators = Counter()
        self.field_paths = set()
        self.string_patterns = Counter()
        self.mapping_files = []
        
    def find_mapping_files(self):
        """Find all mapping files in org_id/PocketMapper/work/collection_name/ directories"""
        # Look for org directories
        for org_dir in self.base_path.iterdir():
            if not org_dir.is_dir():
                continue
                
            # Look for PocketMapper/work structure
            work_dir = org_dir / 'PocketMapper' / 'work'
            if not work_dir.exists() or not work_dir.is_dir():
                # Also try work2, work_test etc
                for pm_dir in org_dir.glob('PocketMapper/work*'):
                    if pm_dir.is_dir():
                        self.process_work_directory(pm_dir)
                continue
            
            self.process_work_directory(work_dir)
                    
        print(f"Found {len(self.mapping_files)} mapping files to analyze")
        return self.mapping_files
    
    def process_work_directory(self, work_dir):
        """Process a work directory looking for collection directories with mapping files"""
        # Each subdirectory in work is a collection
        for collection_dir in work_dir.iterdir():
            if not collection_dir.is_dir() or collection_dir.name.startswith('__'):
                continue
                
            # Skip .sip.zip directories and output directories
            if collection_dir.name.endswith('.sip.zip') or collection_dir.name == 'output':
                continue
                
            # Find all mapping files in this collection directory
            mapping_files = {}
            for file in collection_dir.glob('*mapping*.xml'):
                # Skip if it's in a subdirectory
                if file.parent != collection_dir:
                    continue
                    
                # Extract the rec-def part
                match = re.match(r'.*mapping_(.+)\.xml$', file.name)
                if match:
                    rec_def = match.group(1)
                    # Keep the latest edited version
                    if rec_def not in mapping_files or file.stat().st_mtime > mapping_files[rec_def]['mtime']:
                        mapping_files[rec_def] = {'path': file, 'mtime': file.stat().st_mtime}
                elif file.name == 'mapping.xml':
                    # Default mapping.xml
                    if 'default' not in mapping_files or file.stat().st_mtime > mapping_files['default']['mtime']:
                        mapping_files['default'] = {'path': file, 'mtime': file.stat().st_mtime}
            
            # Add all latest mapping files for this collection
            for rec_def, info in mapping_files.items():
                self.mapping_files.append(info['path'])
    
    def extract_groovy_code(self, xml_file):
        """Extract all Groovy code snippets from a mapping XML file"""
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            
            groovy_snippets = []
            
            # Find all elements that might contain Groovy code
            for elem in root.iter():
                # Check text content
                if elem.text and elem.text.strip():
                    text = elem.text.strip()
                    if self.looks_like_groovy(text):
                        groovy_snippets.append({
                            'code': text,
                            'tag': elem.tag,
                            'file': str(xml_file),
                            'type': 'text'
                        })
                
                # Check attributes
                for attr_name, attr_value in elem.attrib.items():
                    if attr_value and self.looks_like_groovy(attr_value):
                        groovy_snippets.append({
                            'code': attr_value,
                            'tag': elem.tag,
                            'attr': attr_name,
                            'file': str(xml_file),
                            'type': 'attribute'
                        })
            
            return groovy_snippets
            
        except Exception as e:
            print(f"Error parsing {xml_file}: {e}")
            return []
    
    def looks_like_groovy(self, text):
        """Simple heuristic to detect if text might be Groovy code"""
        groovy_indicators = [
            '${', '{', '}', '.', '()', '?:', '?.', '<<', '>>', 
            '==', '!=', '&&', '||', '->', '=>', '~/', 'it.', 
            'def ', 'if ', 'else ', 'return ', '.collect', '.find',
            '.each', '.split', '.trim', '.replace', 'sanitize',
            '_input.', 'lookup.', '+=', ' + ', ' - ', ' * ', ' / '
        ]
        return any(indicator in text for indicator in groovy_indicators)
    
    def analyze_groovy_snippet(self, snippet):
        """Analyze a single Groovy code snippet to extract patterns"""
        code = snippet['code']
        
        # String interpolation patterns
        string_interpolations = re.findall(r'\$\{([^}]+)\}', code)
        for interpolation in string_interpolations:
            self.string_patterns['${...} interpolation'] += 1
            self.groovy_patterns['string_interpolation'].append(interpolation)
        
        # Method calls
        method_calls = re.findall(r'\.(\w+)\s*\(', code)
        for method in method_calls:
            self.function_calls[method] += 1
            
        # Property access
        property_access = re.findall(r'\.(\w+)(?!\s*\()', code)
        for prop in property_access:
            if not prop.isdigit():  # Exclude numeric indices
                self.groovy_patterns['property_access'].append(prop)
        
        # Loop operators
        if ' * ' in code or code.strip().startswith('*'):
            self.loop_operators['* (map/transform)'] += 1
        if ' ** ' in code:
            self.loop_operators['** (first element)'] += 1
        if ' >> ' in code:
            self.loop_operators['>> (process list)'] += 1
        if ' | ' in code:
            self.loop_operators['| (tuple/zip)'] += 1
        if ' + ' in code and not re.search(r'"\s*\+\s*"', code):  # Not string concat
            self.operators['+ (addition/concat)'] += 1
            
        # Safe navigation
        safe_nav = re.findall(r'\?\.\w+', code)
        if safe_nav:
            self.operators['?. (safe navigation)'] += len(safe_nav)
            
        # Elvis operator
        if '?:' in code:
            self.operators['?: (elvis)'] += 1
            
        # Closures
        closure_patterns = re.findall(r'\{([^}]+)\}', code)
        for closure in closure_patterns:
            if '->' in closure or 'it.' in closure:
                self.groovy_patterns['closures'].append(closure)
                
        # List/array access
        array_access = re.findall(r'\[([^\]]+)\]', code)
        for access in array_access:
            if access.isdigit() or 'it' in access:
                self.groovy_patterns['array_access'].append(access)
                
        # Field paths (e.g., _input.record.field)
        field_paths = re.findall(r'(?:_input|it|record|node)(?:\.\w+)+', code)
        self.field_paths.update(field_paths)
        
        # Special functions
        special_functions = ['sanitize', 'sanitizeURI', 'lookup', 'unique', 
                           'xmlEncode', 'htmlEncode', 'normalize', 'toUpperCase',
                           'toLowerCase', 'trim', 'replace', 'split', 'join']
        for func in special_functions:
            if func in code:
                self.function_calls[func] += 1
                
        # Conditionals
        if 'if' in code or '?' in code and ':' in code:
            self.groovy_patterns['conditionals'].append(code)
            
        # Regular expressions
        regex_patterns = re.findall(r'~/([^/]+)/', code)
        if regex_patterns:
            self.groovy_patterns['regex'].extend(regex_patterns)
            
    def generate_report(self):
        """Generate a comprehensive report of all Groovy patterns found"""
        report = {
            'summary': {
                'total_files_analyzed': len(self.mapping_files),
                'total_groovy_snippets': len(self.groovy_snippets),
                'unique_field_paths': len(self.field_paths),
            },
            'function_calls': dict(self.function_calls.most_common()),
            'operators': dict(self.operators),
            'loop_operators': dict(self.loop_operators),
            'string_patterns': dict(self.string_patterns),
            'groovy_patterns': {
                'string_interpolation': list(set(self.groovy_patterns['string_interpolation']))[:20],
                'property_access': list(set(self.groovy_patterns['property_access']))[:20],
                'closures': list(set(self.groovy_patterns['closures']))[:10],
                'array_access': list(set(self.groovy_patterns['array_access']))[:10],
                'conditionals': list(set(self.groovy_patterns['conditionals']))[:10],
                'regex': list(set(self.groovy_patterns['regex']))[:10],
            },
            'field_paths_sample': list(self.field_paths)[:30],
            'sample_snippets': self.groovy_snippets[:20] if self.groovy_snippets else []
        }
        
        return report
    
    def save_detailed_snippets(self, output_file='groovy_snippets.json'):
        """Save all Groovy snippets for detailed analysis"""
        with open(output_file, 'w') as f:
            json.dump({
                'snippets': self.groovy_snippets,
                'total': len(self.groovy_snippets)
            }, f, indent=2)
    
    def analyze_all(self):
        """Run the complete analysis"""
        # Find all mapping files
        self.find_mapping_files()
        
        # Extract and analyze Groovy code from each file
        for mapping_file in self.mapping_files:
            snippets = self.extract_groovy_code(mapping_file)
            self.groovy_snippets.extend(snippets)
            
            for snippet in snippets:
                self.analyze_groovy_snippet(snippet)
        
        # Generate report
        report = self.generate_report()
        
        # Save results
        with open('groovy_analysis_report.json', 'w') as f:
            json.dump(report, f, indent=2)
            
        self.save_detailed_snippets()
        
        # Print summary
        print("\n=== Groovy Code Analysis Summary ===")
        print(f"Total mapping files analyzed: {report['summary']['total_files_analyzed']}")
        print(f"Total Groovy snippets found: {report['summary']['total_groovy_snippets']}")
        print(f"Unique field paths: {report['summary']['unique_field_paths']}")
        
        print("\n=== Top Function Calls ===")
        for func, count in list(report['function_calls'].items())[:15]:
            print(f"  {func}: {count}")
            
        print("\n=== Operators Used ===")
        for op, count in report['operators'].items():
            print(f"  {op}: {count}")
            
        print("\n=== Loop Operators ===")
        for op, count in report['loop_operators'].items():
            print(f"  {op}: {count}")
            
        return report


if __name__ == "__main__":
    import sys
    
    # Default to parent directory if no path provided
    base_path = sys.argv[1] if len(sys.argv) > 1 else ".."
    
    print(f"Analyzing mapping files in: {base_path}")
    analyzer = MappingAnalyzer(base_path)
    report = analyzer.analyze_all()
    
    print("\nAnalysis complete!")
    print("Results saved to:")
    print("  - groovy_analysis_report.json (summary)")
    print("  - groovy_snippets.json (all snippets)")