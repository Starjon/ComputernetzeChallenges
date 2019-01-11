@echo off
:: The IPv6 address of the challenge server:
set remote_IPv6=2001:67c:2564:a170:204:23ff:fede:4b2c

:: The port on which the proxy listens:
set port=1234

start TCP-Hack-Proxy.exe %remote_IPv6% %port%
