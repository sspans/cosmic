import traceback

from nose.plugins.attrib import attr

from marvin.cloudstackAPI import replaceNetworkACLList

from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.base import (
    Domain,
    Account,
    VPC,
    VirtualMachine,
    Network,
    NetworkACL,
    PublicIPAddress,
    NetworkACLList)

from marvin.lib.utils import (
    cleanup_resources,
    random_gen
)
from marvin.utils.MarvinLog import MarvinLog
from marvin.lib.common import (
    get_zone,
    get_network
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
            self.logger.debug(">>>>>>>>>>>> " + traceback.format_exc())
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

        for account in domain['data']['accounts']:
            self.deploy_account(account, domain_obj)

    def deploy_account(self, account, domain_obj):
        self.logger.debug("Deploying account: " + account['data']['username'])
        account_obj = Account.create(
            api_client=self.api_client,
            services=account['data'],
            domainid=domain_obj.uuid
        )
        self.class_cleanup.append(account_obj)

        for vpc in account['data']['vpcs']:
            self.deploy_vpc(vpc, account['data']['virtualmachines'], account_obj)

        for vm in account['data']['virtualmachines']:
            self.deploy_vm(vm, account_obj)

    def deploy_vpc(self, vpc, virtualmachines, account_obj):
        self.logger.debug("Deploying vpc: " + vpc['data']['name'])
        try:
            # TODO -> A LOT!
            vpc_obj = VPC.create(
                api_client=self.api_client,
                data=vpc['data'],
                zone=self.zone,
                account=account_obj
            )
        except:
            self.logger.debug(">>>>>>>>>>>> " + traceback.format_exc())
            raise

        print(">>>>>>>>>>>")
        print(vars(vpc_obj))

        for network in vpc['data']['networks']:
            self.deploy_network(network, vpc_obj)

        self.deploy_acls(vpc['data']['acls'], vpc_obj)

        for publicipaddress in vpc['data']['publicipaddresses']:
            self.deploy_publicipaddress(publicipaddress, virtualmachines, vpc_obj)

    def deploy_network(self, network, vpc_obj):
        self.logger.debug("Deploying network: " + network['data']['name'])
        try:
            network_obj = Network.create(
                self.api_client,
                data=network['data'],
                vpc=vpc_obj,
                zone=self.zone
            )
        except:
            self.logger.debug(">>>>>>>>>>>> " + traceback.format_exc())
            raise

        print(">>>>>>>>>>>")
        print(vars(network_obj))

    def deploy_publicipaddress(self, publicipaddress, virtualmachines, vpc_obj):
        self.logger.debug("Deploying public IP address for vpc: " + vpc_obj.name)
        # for vm in virtualmachines:
        #     if vm['data']['name'] == publicipaddress['data']['name']:

        publicipaddress_obj = PublicIPAddress.create(
            api_client=self.api_client,
            data=publicipaddress['data'],
            vpc=vpc_obj
        )

    def deploy_vm(self, vm, account_obj):
        self.logger.debug("Deploying virtual machine: " + vm['data']['name'])
        try:
            network_list = []
            for nic in vm['data']['nics']:
                network = get_network(
                    api_client=self.api_client,
                    name=nic['data']['networkname']
                )
                network_list.append(network)
                print("!!!!!!!!!!!!!!! \n", network_list, "\n!!!!!!!!!!!!!!!!!")
            vm_obj = VirtualMachine.create(
                self.api_client,
                data=vm['data'],
                zone=self.zone,
                account=account_obj,
                networks=network_list
            )
        except:
            self.logger.debug(">>>>>>>>>>> " + traceback.format_exc())
            raise

    def deploy_acls(self, acls, vpc_obj):
        self.logger.debug("Deploying acls ")
        try:
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
                    print(">>>>>>>>>>>>>")
                    print(vars(rule_obj))
        except:
            self.logger.debug(">>>>>>>>>>>> " + traceback.format_exc())
            raise