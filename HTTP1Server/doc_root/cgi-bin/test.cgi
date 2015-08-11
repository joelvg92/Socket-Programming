#!/usr/bin/env python

import os
import sys

def writeHTML(content):
    sys.stdout.write("<html>\n<body>" + content + "</body>\n</html>")

content_length = 0
try:
    content_length = int(os.environ.get("CONTENT_LENGTH", ""))
except ValueError:
    writeHTML("Invalid CONTENT_LENGTH")
    sys.exit()

payload = sys.stdin.read(content_length)

html = """
<table border=\"1\">
<tr><td>CONTENT_LENGTH</td><td>{content_length}</td></tr>
<tr><td>SCRIPT_NAME</td><td>{script_name}</td></tr>
<tr><td>HTTP_FROM</td><td>{http_from}</td></tr>
<tr><td>HTTP_USER_AGENT</td><td>{http_user_agent}</td></tr>
<tr><td>Payload</td><td>{payload}</td></tr>
</table>
"""

writeHTML(html.format(
    content_length=os.environ.get("CONTENT_LENGTH", ""),
    script_name=os.environ.get("SCRIPT_NAME", ""),
    http_from=os.environ.get("HTTP_FROM", ""),
    http_user_agent=os.environ.get("HTTP_USER_AGENT", ""),
    payload=payload))