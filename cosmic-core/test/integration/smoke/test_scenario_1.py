from nose.plugins.attrib import attr

from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.lib.base import (
    Domain,
    Account
)

from marvin.lib.utils import (
    cleanup_resources,
    random_gen
)
from marvin.utils.MarvinLog import MarvinLog


class TestScenario1(cloudstackTestCase):
    @classmethod
    def setUpClass(cls):
        cls.logger = MarvinLog(MarvinLog.LOGGER_TEST).get_logger()

        cls.test_client = super(TestScenario1, cls).getClsTestClient()
        cls.api_client = cls.test_client.getApiClient()

        # Retrieve test data
        cls.services = cls.test_client.getParsedTestDataConfig().copy()

        cls.class_cleanup = []

    @classmethod
    def tearDownClass(cls):

        try:
            cleanup_resources(cls.api_client, cls.class_cleanup, cls.logger)

        except Exception as e:
            raise Exception("Exception: %s" % e)

    def setUp(self):

        self.method_cleanup = []

    def tearDown(self):

        try:
            cleanup_resources(self.api_client, self.method_cleanup, self.logger)

        except Exception as e:
            raise Exception("Exception: %s" % e)

    @attr(tags=['advanced'])
    def test_01(self):

        self.setup_infra(self.services['scenario_1'])

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
                self.api_client,
                name=domain['data']['name']
            )

            # TODO: Error handling
            domain_obj = domain_list[0]
        else:
            self.logger.debug("Creating domain: " + domain['data']['name'] + "-" + random_string)
            domain_obj = Domain.create(
                self.api_client,
                name=domain['data']['name'] + "-" + random_string
            )

        for account in domain['data']['accounts']:
            self.deploy_account(account, domain_obj)

    def deploy_account(self, account, domain_obj):
        self.logger.debug("Deploying account: " + account['data']['username'])

        account_obj = Account.create(
            self.api_client,
            services=account,
            domainid=domain_obj.uuid
        )

        for vpc in account['data']['vpcs']:
            self.deploy_vpc(vpc, account_obj)

    def deploy_vpc(self, vpc, account_obj):
        self.logger.debug("Deploying vpc: " + vpc['data']['name'])

        # TODO -> A LOT!

