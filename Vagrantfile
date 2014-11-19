Vagrant.configure('2') do |config|
  config.vm.define 'fhirbase' do |fhirbase|
    fhirbase.vm.provider 'docker' do |docker|
      docker.image = 'fhirbase/fhirbase'
      docker.ports = ['5433:5432']
      docker.name = 'fhirbase'
      docker.vagrant_vagrantfile = './Vagrantfile.proxy'
    end
  end

  config.vm.define 'fhirplace' do |fhirplace|
    fhirplace.vm.provider 'docker' do |docker|
      docker.image = 'fhirbase/fhirplace'
      docker.ports = ['3000:3000']
      docker.name = 'fhirplace'
      docker.link('fhirbase:db')

      fhirplace_home = ENV['FHIRPLACE_HOME']
      if fhirplace_home && fhirplace_home != ''
        docker.volumes = ["#{fhirplace_home}:/app"]
      end

      docker.create_args = ['-t', '-i']
      docker.vagrant_vagrantfile = './Vagrantfile.proxy'
    end
  end
end
