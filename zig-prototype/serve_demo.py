#!/usr/bin/env python3
"""Simple HTTP server for WASM demo"""

import http.server
import socketserver
import os

PORT = 8000

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # Add CORS headers for WASM
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cross-Origin-Embedder-Policy', 'require-corp')
        self.send_header('Cross-Origin-Opener-Policy', 'same-origin')
        # Correct MIME type for WASM
        if self.path.endswith('.wasm'):
            self.send_header('Content-Type', 'application/wasm')
        # Ensure UTF-8 encoding for HTML files
        elif self.path.endswith('.html'):
            self.send_header('Content-Type', 'text/html; charset=utf-8')
        super().end_headers()

os.chdir(os.path.dirname(os.path.abspath(__file__)))

with socketserver.TCPServer(("", PORT), MyHTTPRequestHandler) as httpd:
    print(f"Server running at http://localhost:{PORT}/")
    print(f"Open http://localhost:{PORT}/wasm_demo.html in your browser")
    print("Press Ctrl+C to stop the server")
    httpd.serve_forever()