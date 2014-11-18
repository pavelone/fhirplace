# fhirplace

[![Build Status](https://travis-ci.org/fhirbase/fhirplace.svg)](https://travis-ci.org/fhirbase/fhirplace)

FHIR Server implementation powered by
[FHIRBase](https://github.com/fhirbase/fhirbase).

## Installation

FHIRPlace and FHIRBase both are using a lot of third-party software
like PostgreSQL, Java, Node.js, etc. Installing it by hand is an
complex task which can take several working days to accomplish.

Fortunatelly, another approach is available nowadays. Instead of
installing all required software manually, one can use virtualization
technologies to run a "virtual machine" with alredy pre-configured
operating system.

To quickly get FHIRPlace up and running we advice you to follow
virtualization path. If you're willing to install FHIRPlace to your
local machine or server, please follow full
[Installation Manual](https://github.com/fhirbase/fhirplace/wiki/InstallationManual).

## Running FHIRPlace's Virtual Machine

### Installing VirtualBox

If you're running Windows or Mac OS X, you have to install
[VirtualBox](https://www.virtualbox.org/) virtualization
software. Installation process is quite straightforward, so we don't
describe it here. If problems arise, refer to
[Official Installation Manual](https://www.virtualbox.org/manual/ch02.html).

If you're running some flavor of Linux, you don't have to install
VirtualBox, just follow to next step.

### Installing Vagrant

[Vagrant](https://www.vagrantup.com/) is a simple and powerful
command-line utility to manage virtual machines primary intended for
developers use. Navigate to
[downloads page](https://www.vagrantup.com/downloads.html) to get an
installer suitable for your OS. After installation is finished, run
Terminal application (on Mac OS X) or Command Prompt (on Windows) to
test if installation was successful. Type following command:

```bash
vagrant --version
```

If you see output like

```bash
Vagrant 1.6.5
```

you had successfuly installed Vagrant.

### Getting FHIRPlace source code

You have to have FHIRPlace source code to run FHIRPlace. If you have
[Git](http://git-scm.com/) SCM installed, clone FHIRPlace repository
from GitHub. Alternatively, you can download
[ZIP archive](https://github.com/fhirbase/fhirplace/archive/master.zip)
and unpack it somewhere in your file system.

### Starting VM

Open Terminal (or Command Prompt) and navigate to directory where
FHIRPlace's source code is located:

```bash
cd ~/work/fhirplace
```

Let FRONTEND be absolute path to your frontend project that will use
fhirplace

```
sudo docker.io run -d --name=fhirbase -t -p 5432 fhirbase/fhirbase:latest
sudo docker.io run -d --name=fhirplace -p 3000:3000 -v FRONTEND:/app --link fhirbase:db -t -i fhirbase/fhirplace:latest
```

### Mac OS X & Windows

```bash
vagrant up
```

### Check

curl http://localhost:3000/Patient/_search

```

## Usage

```
Root for you FRONTEND project: http://localhost:3000/app/
Simple access to fhirplace server: http://localhost:3000/fhirface/
```

## Service

> All premium services from developers of Fhirbase projects
> should be requested from Choice Hospital Systems (http://Choice-HS.com)

## License

Copyright © 2014 Health Samurai Team.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
