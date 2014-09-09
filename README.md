# fhirplace

FHIR server implementation

## Installation

Install docker

```
alias d='sudo docker'

sudo docker.io run -d --name=fhirbase -t -p 5432 fhirbase/fhirbase:v0.0.1
sudo docker.io run -d --name=fhirplace -p 3000:3000 --link fhirbase:db -t -i fhirbase/fhirplace:v0.0.1

curl http://localhost:3000
```


## Usage


## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
