#!/usr/bin/env python

#
# Client usage examples;
# Getting password:
#   wget -q -t 3 -T 20 -O - --header 'DomU_Request: send_my_password' <routerIP>:8080
# Send ack:
#   wget -t 3 -T 20 -O - --header 'DomU_Request: saved_password' localhost:8080
# Save password only from within router:
#   /opt/cosmic/router/scripts/savepassword.sh -v <IP> -p <password>
#   curl --header 'DomU_Request: save_password' http://localhost:8080/ -F ip=<IP> -F password=<passwd>

import binascii
import cgi
import os
import sys
import syslog
import threading

from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
from SocketServer import ThreadingMixIn  # , ForkingMixIn

passMap = { }
secureToken = None
listeningAddress = '127.0.0.1'
lock = threading.RLock()


def getTokenFile():
    return '/tmp/passwdsrvrtoken'


def getPasswordFile():
    return '/var/cache/cloud/passwords-%s' % listeningAddress


def initToken():
    global secureToken
    if os.path.exists(getTokenFile()):
        with open(getTokenFile(), 'r') as f:
            secureToken = f.read()
    if not secureToken:
        secureToken = binascii.hexlify(os.urandom(16))
        with open(getTokenFile(), 'w') as f:
            f.write(secureToken)


def checkToken(token):
    return token == secureToken


def loadPasswordFile():
    try:
        with file(getPasswordFile()) as f:
            for line in f:
                if '=' not in line: continue
                key, value = line.strip().split('=', 1)
                passMap[key] = value
    except IOError:
        pass


def savePasswordFile():
    with lock:
        try:
            with file(getPasswordFile(), 'w') as f:
                for ip in passMap:
                    f.write('%s=%s\n' % (ip, passMap[ip]))
            f.close()
        except IOError as e:
            syslog.syslog('serve_password[%s]: Unable to save to password file %s' % (listeningAddress, e))


def getPassword(ip):
    return passMap.get(ip, None)


def setPassword(ip, password):
    if not ip or not password:
        return
    with lock:
        passMap[ip] = password


def removePassword(ip):
    with lock:
        if ip in passMap:
            del passMap[ip]


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    pass


class PasswordRequestHandler(BaseHTTPRequestHandler):
    server_version = 'CloudStack Password Server'
    sys_version = '4.x'

    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.send_header('Server', 'CloudStack Password Server')
        self.end_headers()
        requestType = self.headers.get('DomU_Request')
        clientAddress = self.client_address[0]
        if requestType == 'send_my_password':
            password = getPassword(clientAddress)
            if not password:
                self.wfile.write('saved_password')
                syslog.syslog('serve_password[%s]: requested password not found for %s' % (listeningAddress, clientAddress))
            else:
                self.wfile.write(password)
                syslog.syslog('serve_password[%s]: password sent to %s' % (listeningAddress, clientAddress))
        elif requestType == 'saved_password':
            removePassword(clientAddress)
            savePasswordFile()
            self.wfile.write('saved_password')
            syslog.syslog('serve_password[%s]: saved_password ack received from %s' % (listeningAddress, clientAddress))
        else:
            self.send_response(400)
            self.wfile.write('bad_request')
            syslog.syslog('serve_password[%s]: bad_request from IP %s' % (listeningAddress, clientAddress))
        return

    def do_POST(self):
        form = cgi.FieldStorage(
            fp=self.rfile,
            headers=self.headers,
            environ={ 'REQUEST_METHOD': 'POST',
                      'CONTENT_TYPE': self.headers['Content-Type'],
                      })
        self.send_response(200)
        self.end_headers()
        clientAddress = self.client_address[0]
        if clientAddress not in ['localhost', '127.0.0.1', listeningAddress]:
            syslog.syslog('serve_password[%s]: non-localhost IP trying to save password: %s' % (listeningAddress, clientAddress))
            self.send_response(403)
            return
        if 'ip' not in form or 'password' not in form or 'token' not in form or self.headers.get('DomU_Request') != 'save_password':
            syslog.syslog('serve_password[%s]: request trying to save password does not contain both ip and password' % listeningAddress)
            self.send_response(403)
            return
        token = form['token'].value
        if not checkToken(token):
            syslog.syslog('serve_password[%s]: invalid save_password token received from %s' % (listeningAddress, clientAddress))
            self.send_response(403)
            return
        ip = form['ip'].value
        password = form['password'].value
        if not ip or not password:
            syslog.syslog('serve_password[%s]: empty ip/password[%s/%s] received from savepassword' % (listeningAddress, ip, password))
            return
        syslog.syslog('serve_password[%s]: password saved for VM IP %s' % (listeningAddress, ip))
        setPassword(ip, password)
        savePasswordFile()
        return

    def log_message(self, format, *args):
        return


def serve(HandlerClass=PasswordRequestHandler,
          ServerClass=ThreadedHTTPServer):
    global listeningAddress
    if len(sys.argv) > 1:
        listeningAddress = sys.argv[1]

    server_address = (listeningAddress, 8080)
    passwordServer = ServerClass(server_address, HandlerClass)
    passwordServer.allow_reuse_address = True
    sa = passwordServer.socket.getsockname()
    initToken()
    loadPasswordFile()
    syslog.syslog('serve_password[%s] running on %s:%s' % (listeningAddress, sa[0], sa[1]))
    try:
        passwordServer.serve_forever()
    except KeyboardInterrupt:
        syslog.syslog('serve_password[%s] shutting down' % listeningAddress)
        passwordServer.socket.close()
    except Exception as e:
        syslog.syslog('serve_password[%s] hit exception %s -- died' % (listeningAddress, e))
        passwordServer.socket.close()


if __name__ == '__main__':
    serve()
