# -*- mode: ruby -*-
# vi: set ft=ruby :

# All Vagrant configuration is done below. The "2" in Vagrant.configure
# configures the configuration version (we support older styles for
# backwards compatibility). Please don't change it unless you know what
# you're doing.
# Vagrant::DEFAULT_SERVER_URL.replace('https://vagrantcloud.com')

Vagrant.configure(2) do |config|
  # The most common configuration options are documented and commented below.
  # For a complete reference, please see the online documentation at
  # https://docs.vagrantup.com.

  # Every Vagrant development environment requires a box. You can search for
  # boxes at https://atlas.hashicorp.com/search.
  config.vm.box = "folio/testing-backend"
  
  # We don't specify this any more to make sure that developers can pull the latest version. be aware that
  # this exposes you to bugs that may be published and gives you a slightly less stable base to build on
  # Relvant vagrant commands
  #   
  #     vagrant box list -- list the currently installed images
  #     vagrant box prune -- prune any unused version - free disk space
  #     vagrant box update -- update the current box to the latest version
  #   
  #config.vm.box_version  = "5.0.0-20200414.4050"

  config.vm.provider "virtualbox" do |v|
    v.memory = 16384
    v.cpus = 4
  end

  #config.vm.provision "docker" do |d|
    # docker run -d --restart always --hostname rabbitmq -p 15672:15672 -p 5672:5672 --name rabbitmq -e RABBITMQ_DEFAULT_USER=adm -e RABBITMQ_DEFAULT_PASS=admpass rabbitmq:management
 #   d.run "rabbitmq",
 #     args: "--hostname rabbitmq -p 15672:15672 -p 5672:5672 --name rabbitmq -e RABBITMQ_DEFAULT_USER=adm -e RABBITMQ_DEFAULT_PASS=admpass"

    # Es 5 missing so far:: ulimits: memlock: soft: -1 hard: -1 mem_limit: 1g volumes: - esdata:/usr/share/elasticsearch/data
    # d.run "docker.elastic.co/elasticsearch/elasticsearch:5.4.3",
    #   args: "-name es5 -p 9200:9200 -p 9300:9300 -e cluster.name=kbplusg3 -e bootstrap.memory_lock=true -e 'ES_JAVA_OPTS=-Xms512m -Xmx512m' -e http.host=0.0.0.0 -e transport.host=0.0.0.0"

    # Virtuoso
    # docker run --name my-virtuoso -p 8890:8890 -p 1111:1111 -e DBA_PASSWORD=myDbaPassword -e SPARQL_UPDATE=true -e DEFAULT_GRAPH=http://www.example.com/my-graph -v /my/path/to/the/virtuoso/db:/data -d tenforce/virtuoso
 #   d.run "tenforce/virtuoso",
 #     name: "virtuoso",
 #     args: "-p 8890:8890 -p 1111:1111 -e DBA_PASSWORD=myDbaPassword -e SPARQL_UPDATE=true -e DEFAULT_GRAPH=http://www.folio.org " # -v virtdata:/data"

 # end
  # Disable automatic box update checking. If you disable this, then
  # boxes will only be checked for updates when the user runs
  # `vagrant box outdated`. This is not recommended.
  # config.vm.box_check_update = false

  # Expose the postgres instance that is installed so that apps running outside the
  # vbox instance can use it.
  config.vm.network "forwarded_port", guest: 5432, host: 54321
  config.vm.network "forwarded_port", guest: 9130, host: 9130


  # Create a forwarded port mapping which allows access to a specific port
  # within the machine from a port on the host machine. In the example below,
  # accessing "localhost:8080" will access port 80 on the guest machine.
  # config.vm.network "forwarded_port", guest: 80, host: 8080

  # Create a private network, which allows host-only access to the machine
  # using a specific IP.
  # config.vm.network "private_network", ip: "192.168.33.10"

  # Create a public network, which generally matched to bridged network.
  # Bridged networks make the machine appear as another physical device on
  # your network.
  # config.vm.network "public_network"

  # Share an additional folder to the guest VM. The first argument is
  # the path on the host to the actual folder. The second argument is
  # the path on the guest to mount the folder. And the optional third
  # argument is a set of non-required options.
  # config.vm.synced_folder "../data", "/vagrant_data"

  # Provider-specific configuration so you can fine-tune various
  # backing providers for Vagrant. These expose provider-specific options.
  # Example for VirtualBox:
  #
  # config.vm.provider "virtualbox" do |vb|
  #   # Display the VirtualBox GUI when booting the machine
  #   vb.gui = true
  #
  #   # Customize the amount of memory on the VM:
  #   vb.memory = "1024"
  # end
  #
  # View the documentation for the provider you are using for more
  # information on available options.

  # Define a Vagrant Push strategy for pushing to Atlas. Other push strategies
  # such as FTP and Heroku are also available. See the documentation at
  # https://docs.vagrantup.com/v2/push/atlas.html for more information.
  # config.push.define "atlas" do |push|
  #   push.app = "YOUR_ATLAS_USERNAME/YOUR_APPLICATION_NAME"
  # end

  # Enable provisioning with a shell script. Additional provisioners such as
  # Puppet, Chef, Ansible, Salt, and Docker are also available. Please see the
  # documentation for more information about their specific syntax and use.
  # config.vm.provision "shell", inline: <<-SHELL
  #   sudo apt-get update
  #   sudo apt-get install -y apache2
  # SHELL
end
