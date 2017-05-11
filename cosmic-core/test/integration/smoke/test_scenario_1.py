import traceback

from nose.plugins.attrib import attr

from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.base import (
    Domain,
    Account,
    VPC,
    VirtualMachine,
    Network,
    NetworkACL,
    PublicIPAddress,
    NetworkACLList,
    NATRule
)

from marvin.lib.utils import (
    cleanup_resources,
    random_gen
)
from marvin.utils.MarvinLog import MarvinLog
from marvin.lib.common import (
    get_zone,
    get_network,
    get_virtual_machine,
    get_vpc,
    get_network_acl
)


class TestScenario1(cloudstackTestCase):
    @classmethod
    def setUpClass(cls):
        cls.logger = MarvinLog(MarvinLog.LOGGER_TEST).get_logger()

        cls.test_client = super(TestScenario1, cls).getClsTestClient()
        cls.api_client = cls.test_client.getApiClient()

        cls.zone = get_zone(cls.api_client, cls.test_client.getZoneForTests())

        # Retrieve test data
        cls.services = cls.test_client.getParsedTestDataConfig().copy()

        cls.class_cleanup = []

    @classmethod
    def tearDownClass(cls):

        try:
            cleanup_resources(cls.api_client, cls.class_cleanup, cls.logger)

        except:
            raise

    def setUp(self):

        self.method_cleanup = []

    def tearDown(self):

        try:
            cleanup_resources(self.api_client, self.method_cleanup, self.logger)
        except:
            raise

    @attr(tags=['advanced'])
    def test_01(self):
        try:
            self.setup_infra(self.services['scenario_1'])
        except:
            self.logger.debug("!!!!!!!!!!!!!!!!!!! " + traceback.format_exc())
            raise

    def setup_infra(self, scenario):
        self.logger.debug("Deploying scenario")

        for domain in scenario['data']['domains']:
            self.deploy_domain(domain)

    def deploy_domain(self, domain):
        self.logger.debug("Deploying domain: " + domain['data']['name'])

        random_string = random_gen()

        if domain['data']['name'] == 'ROOT':
            self.logger.debug("ROOT domain selected, not creating.")
            domain_list = Domain.list(
                api_client=self.api_client,
                name=domain['data']['name']
            )

            # TODO: Error handling
            domain_obj = domain_list[0]
        else:
            self.logger.debug("Creating domain: " + domain['data']['name'] + "-" + random_string)
            domain_obj = Domain.create(
                api_client=self.api_client,
                name=domain['data']['name'] + "-" + random_string
            )
            domain['data']['name'] = domain_obj.name
            self.logger.debug("Deployed domain: " + domain['data']['name'])


        for account in domain['data']['accounts']:
            self.deploy_account(account, domain_obj)

    def deploy_account(self, account, domain_obj):
        self.logger.debug("Deploying account: " + account['data']['username'])
        account_obj = Account.create(
            api_client=self.api_client,
            services=account['data'],
            domainid=domain_obj.uuid
        )
        account['data']['username'] = account_obj.name
        self.logger.debug("Deployed account: " + account['data']['username'])

        self.class_cleanup.append(account_obj)

        for vpc in account['data']['vpcs']:
            self.deploy_vpc(vpc, account_obj)

        for vm in account['data']['virtualmachines']:
            self.deploy_vm(vm, account_obj)

        for vpc in account['data']['vpcs']:
            self.deploy_vpc_public_ips(vpc, account['data']['virtualmachines'])

    def deploy_vpc(self, vpc, account_obj):
        self.logger.debug("Deploying vpc: " + vpc['data']['name'])
        # TODO -> A LOT!
        vpc_obj = VPC.create(
            api_client=self.api_client,
            data=vpc['data'],
            zone=self.zone,
            account=account_obj
        )
        vpc['data']['name'] = vpc_obj.name
        self.logger.debug("Deployed vpc: " + vpc['data']['name'])

        print(">>>>>>>>>>> VPC")
        print(vars(vpc_obj))

        self.deploy_acls(vpc['data']['acls'], vpc_obj)

        for network in vpc['data']['networks']:
            self.deploy_network(network, vpc_obj)

    def deploy_vpc_public_ips(self, vpc, virtualmachines):
        self.logger.debug("Deploying vpc: " + vpc['data']['name'])

        vpc_obj = get_vpc(self.api_client, vpc['data']['name'])

        for publicipaddress in vpc['data']['publicipaddresses']:
            self.deploy_publicipaddress(publicipaddress, virtualmachines, vpc_obj)

    def deploy_network(self, network, vpc_obj):
        self.logger.debug("Deploying network: " + network['data']['name'])

        acl_obj = get_network_acl(self.api_client, name=network['data']['aclname'])

        network_obj = Network.create(
            self.api_client,
            data=network['data'],
            vpc=vpc_obj,
            zone=self.zone,
            acl=acl_obj
        )
        network['data']['name'] = network_obj.name
        self.logger.debug("Deployed network: " + network['data']['name'])

        print(">>>>>>>>>>> NETWORK")
        print(vars(network_obj))

    def deploy_publicipaddress(self, publicipaddress, virtualmachines, vpc_obj):
        self.logger.debug("Deploying public IP address for vpc: " + vpc_obj.name)
        publicipaddress_obj = PublicIPAddress.create(
            api_client=self.api_client,
            data=publicipaddress['data'],
            vpc=vpc_obj
        )
        print(">>>>>>>>> PUBLIC IP")
        print(vars(publicipaddress_obj))
        for portforward in publicipaddress['data']['portforwards']:
            network_name = None
            for vm in virtualmachines:
                if vm['data']['name'] == portforward['data']['virtualmachinename']:
                    for nic in vm['data']['nics']:
                        if nic['data']['guestip'] == portforward['data']['nic']:
                            network_name = nic['data']['networkname']
                            network_obj = get_network(self.api_client, name=network_name, vpc=vpc_obj)
                            vm_obj = get_virtual_machine(self.api_client, name=vm['data']['name'], vpc=vpc_obj)
                            nat_rule = NATRule.create(
                                api_client=self.api_client,
                                virtual_machine=vm_obj,
                                data=portforward['data'],
                                vpc=vpc_obj,
                                network=network_obj,
                                ipaddress=publicipaddress_obj
                            )

    def deploy_vm(self, vm, account_obj):
        self.logger.debug("Deploying virtual machine: " + vm['data']['name'])
        network_list = []
        for nic in vm['data']['nics']:
            network = get_network(
                api_client=self.api_client,
                name=nic['data']['networkname']
            )
            network_list.append(network)
            print(">>>>>>>>> NTWRK LIST")
            print(network_list)
        vm_obj = VirtualMachine.create(
            self.api_client,
            data=vm['data'],
            zone=self.zone,
            account=account_obj,
            networks=network_list
        )
        print(">>>>>>>>> VIRTUAL MACHINE")
        print(vars(vm_obj))

    def deploy_acls(self, acls, vpc_obj):
        self.logger.debug("Deploying acls ")
        for acl in acls:
            acls_list = NetworkACLList.create(
                api_client=self.api_client,
                data=acl['data'],
                vpc=vpc_obj
            )
            for rule in acl['data']['rules']:
                rule_obj = NetworkACL.create(
                    api_client=self.api_client,
                    data=rule,
                    acl=acls_list
                )
                print(">>>>>>>>>>>>> ACL RULE")
                print(vars(rule_obj))
