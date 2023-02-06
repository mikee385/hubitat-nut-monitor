# hubitat-nut-monitor
NUT configuration files, scripts, and Hubitat app and driver files that can be used for monitoring a NUT server without polling

## Instructions
1. Install `nut` using `apt-get install nut` on the server
2. Copy `nut.conf` to `/etc/nut/nut.conf`
3. Figure out the correct settings for your device and replace them in `ups.conf`
4. Copy `ups.conf` to `/etc/nut/ups.conf`
5. Copy `upsd.conf` to `/etc/nut/upsd.conf`
6. Replace `<password>` in `upsd.users` with a real password
7. Copy `upsd.users` to `/etc/nut/upsd.users`
8. Replace `<password>` in `upsmon.conf` with the same password from `upsd.users`
9. Copy `upsmon.conf` to `/etc/nut/upsmon.conf`
10. Reboot the server
11. Create a new driver on Hubitat using the code from `nut-monitor-driver.groovy`
12. Create a new app on Hubitat using the code from `nut-monitor-app.groovy`
13. Install a User App for `NUT Monitor` on Hubitat
14. Configure the `NUT Monitor` app on Hubitat using the IP address of the server, `3493` for the port number, and `BackupBattery` for the UPS name
15. Go to the status page of the `NUT Monitor` app on Hubitat and copy the URL for `statusUrl`
16. Replace the URL in `hubitat-nut-status.sh` with the URL from `statusUrl`
17. Copy `hubitat-nut-status.sh` to `/usr/local/bin/hubitat-nut-status.sh`
18. Make `hubitat-nut-status.sh` executable using the command `sudo chmod 777 /usr/local/bin/hubitat-nut-status.sh`
19. Copy `upssched.conf` to `/etc/nut/upssched.conf`
20. Copy `hubitat-nut-monitor.service` to `etc/systemd/system/hubitat-nut-monitor.service`
21. Enable `hubitat-nut-monitor.service` using the command `sudo systemctl enable hubitat-nut-monitor.service`
22. Reboot the server

Everything should be set up and running now!