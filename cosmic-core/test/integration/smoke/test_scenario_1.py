import sys
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
        cls.test_data = cls.test_client.getParsedTestDataConfig()
        cls.zone = get_zone(cls.api_client, cls.test_client.getZoneForTests())

        cls.class_cleanup = []

    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.api_client, cls.class_cleanup, cls.logger)
        except:
            sys.exit(1)

    def setUp(self):
        self.method_cleanup = []

    def tearDown(self):
        try:
            cleanup_resources(self.api_client, self.method_cleanup, self.logger)
        except:
            sys.exit(1)

    @attr(tags=['advanced'])
    def test_01(self):
        try:
            self.setup_infra(self.test_data['scenario_1']['data'])
        except:
            self.logger.debug('>>>>> STACKTRACE >>>>>' + traceback.format_exc())
            sys.exit(1)

    def setup_infra(self, scenario_data):
        for domain in scenario_data['domains']:
            self.deploy_domain(domain['data'])

    def deploy_domain(self, domain_data):
        if domain_data['name'] == 'ROOT':
            domain_list = Domain.list(
                api_client=self.api_client,
                name=domain_data['name']
            )
            domain = domain_list[0]
        else:
            domain = Domain.create(
                api_client=self.api_client,
                name=domain_data['name'] + '-' + random_gen()
            )
            domain_data['name'] = domain.name

        for account in domain_data['accounts']:
            self.deploy_account(account['data'], domain)

    def deploy_account(self, account_data, domain):
        account = Account.create(
            api_client=self.api_client,
            services=account_data,
            domainid=domain.uuid
        )
        # self.class_cleanup.append(account)
        account_data['username'] = account.name

        for vpc in account_data['vpcs']:
            self.deploy_vpc(vpc['data'], account)

        for vm in account_data['virtualmachines']:
            self.deploy_vm(vm['data'], account)

        for vpc in account_data['vpcs']:
            self.deploy_vpc_public_ips(vpc['data'], account_data['virtualmachines'])

    def deploy_vpc(self, vpc_data, account):
        vpc = VPC.create(
            api_client=self.api_client,
            data=vpc_data,
            zone=self.zone,
            account=account
        )
        vpc_data['name'] = vpc.name

        self.deploy_acls(vpc_data['acls'], vpc)

        for network in vpc_data['networks']:
            self.deploy_network(network, vpc)

    def deploy_acls(self, acls, vpc_obj):
        for acl in acls:
            acls_list = NetworkACLList.create(
                api_client=self.api_client,
                data=acl['data'],
                vpc=vpc_obj
            )

            for rule in acl['data']['rules']:
                NetworkACL.create(
                    api_client=self.api_client,
                    data=rule,
                    acl=acls_list
                )

    def deploy_network(self, network, vpc_obj):
        acl_obj = get_network_acl(self.api_client, name=network['data']['aclname'])

        network_obj = Network.create(
            self.api_client,
            data=network['data'],
            vpc=vpc_obj,
            zone=self.zone,
            acl=acl_obj
        )
        network['data']['name'] = network_obj.name

    def deploy_vm(self, vm_data, account_obj):
        network_list = []
        for nic in vm_data['nics']:
            network = get_network(
                api_client=self.api_client,
                name=nic['data']['networkname']
            )
            network_list.append(network)

        VirtualMachine.create(
            self.api_client,
            data=vm_data,
            zone=self.zone,
            account=account_obj,
            networks=network_list
        )

    def deploy_vpc_public_ips(self, vpc_data, virtualmachines):
        vpc = get_vpc(self.api_client, vpc_data['name'])

        for publicipaddress in vpc_data['publicipaddresses']:
            self.deploy_publicipaddress(publicipaddress['data'], virtualmachines, vpc)

    def deploy_publicipaddress(self, publicipaddress_data, virtualmachines, vpc):
        publicipaddress = PublicIPAddress.create(
            api_client=self.api_client,
            data=publicipaddress_data,
            vpc=vpc
        )

        for portforward_data in publicipaddress_data['portforwards']:
            for virtualmachine_data in virtualmachines:
                if virtualmachine_data['data']['name'] == portforward_data['virtualmachinename']:
                    for nic_data in virtualmachine_data['data']['nics']:
                        if nic_data['guestip'] == portforward_data['nic']:
                            network = get_network(self.api_client, name=nic_data['networkname'], vpc=vpc)
                            virtualmachine = get_virtual_machine(
                                self.api_client,
                                name=virtualmachine_data['data']['name'],
                                vpc=vpc
                            )
                            NATRule.create(
                                api_client=self.api_client,
                                data=portforward_data,
                                vpc=vpc,
                                network=network,
                                virtual_machine=virtualmachine,
                                ipaddress=publicipaddress
                            )
