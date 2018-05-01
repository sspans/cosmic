package groovy.com.cloud.ldap

import com.cloud.ldap.LdapAuthenticator
import com.cloud.ldap.LdapManager
import com.cloud.ldap.LdapTrustMapVO
import com.cloud.ldap.LdapUser
import com.cloud.legacymodel.user.Account
import com.cloud.legacymodel.user.User
import com.cloud.legacymodel.user.UserAccount
import com.cloud.server.auth.UserAuthenticator
import com.cloud.user.AccountManager
import com.cloud.user.UserAccountVO
import com.cloud.user.dao.UserAccountDao
import com.cloud.utils.Pair

class LdapAuthenticatorSpec extends spock.lang.Specification {

    def "Test a failed authentication due to user not being found within cloudstack"() {
        given: "We have an LdapManager, userAccountDao and ldapAuthenticator and the user doesn't exist within cloudstack."
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        userAccountDao.getUserAccount(_, _) >> null
        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)
        when: "A user authentications"
        def result = ldapAuthenticator.authenticate("rmurphy", "password", 0, null)
        then: "their authentication fails"
        result.first() == false
    }

    def "Test failed authentication due to ldap bind being unsuccessful"() {
        given: "We have an LdapManager, LdapConfiguration, userAccountDao and LdapAuthenticator"
        def ldapManager = Mock(LdapManager)
        def ldapUser = Mock(LdapUser)
        ldapUser.isDisabled() >> false
        ldapManager.isLdapEnabled() >> true
        ldapManager.getUser("rmurphy") >> ldapUser
        ldapManager.canAuthenticate(_, _) >> false

        UserAccountDao userAccountDao = Mock(UserAccountDao)
        userAccountDao.getUserAccount(_, _) >> new UserAccountVO()
        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)

        when: "The user authenticates with an incorrect password"
        def result = ldapAuthenticator.authenticate("rmurphy", "password", 0, null)

        then: "their authentication fails"
        result.first() == false
    }

    def "Test failed authentication due to ldap not being configured"() {
        given: "We have an LdapManager, A configured LDAP server, a userAccountDao and LdapAuthenticator"
        def ldapManager = Mock(LdapManager)
        ldapManager.isLdapEnabled() >> false

        UserAccountDao userAccountDao = Mock(UserAccountDao)
        userAccountDao.getUserAccount(_, _) >> new UserAccountVO()

        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)
        when: "The user authenticates"
        def result = ldapAuthenticator.authenticate("rmurphy", "password", 0, null)
        then: "their authentication fails"
        result.first() == false
    }

    def "Test successful authentication"() {
        given: "We have an LdapManager, LdapConfiguration, userAccountDao and LdapAuthenticator"
        def ldapManager = Mock(LdapManager)
        def ldapUser = Mock(LdapUser)
        ldapUser.isDisabled() >> false
        ldapManager.isLdapEnabled() >> true
        ldapManager.canAuthenticate(_, _) >> true
        ldapManager.getUser("rmurphy") >> ldapUser

        UserAccountDao userAccountDao = Mock(UserAccountDao)
        userAccountDao.getUserAccount(_, _) >> new UserAccountVO()
        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)

        when: "The user authenticates with an incorrect password"
        def result = ldapAuthenticator.authenticate("rmurphy", "password", 0, null)

        then: "their authentication passes"
        result.first() == true
    }

    def "Test that encode doesn't change the input"() {
        given: "We have an LdapManager, userAccountDao and LdapAuthenticator"
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)
        when: "a users password is encoded"
        def result = ldapAuthenticator.encode("password")
        then: "it doesn't change"
        result == "password"
    }

    def "test authentication when ldap is disabled"() {
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        def ldapAuthenticator = new LdapAuthenticator(ldapManager, userAccountDao)
        ldapManager.isLdapEnabled() >> false

        when:
        Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> result = ldapAuthenticator.authenticate("rajanik", "password", 1, null)
        then:
        result.first() == false
        result.second() == null

    }

    // tests when domain is linked to LDAP
    def "test authentication when domain is linked and user disabled in ldap"() {
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        AccountManager accountManager = Mock(AccountManager)

        def ldapAuthenticator = new LdapAuthenticator()
        ldapAuthenticator._ldapManager = ldapManager
        ldapAuthenticator._userAccountDao = userAccountDao
        ldapAuthenticator._accountManager = accountManager

        long domainId = 1;
        String username = "rajanik"
        LdapManager.LinkType type = LdapManager.LinkType.GROUP
        String name = "CN=test,DC=ccp,DC=citrix,DC=com"

        ldapManager.isLdapEnabled() >> true
        UserAccount userAccount = Mock(UserAccount)
        userAccountDao.getUserAccount(username, domainId) >> userAccount
        userAccount.getId() >> 1
        ldapManager.getDomainLinkedToLdap(domainId) >> new LdapTrustMapVO(domainId, type, name, (short) 2)
        ldapManager.getUser(username, type.toString(), name) >> new LdapUser(username, "email", "firstname", "lastname", "principal", "domain", true)
        //user should be disabled in cloudstack
        accountManager.disableUser(1) >> userAccount

        when:
        Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> result = ldapAuthenticator.authenticate(username, "password", domainId, null)
        then:
        result.first() == false
        result.second() == UserAuthenticator.ActionOnFailedAuthentication.INCREMENT_INCORRECT_LOGIN_ATTEMPT_COUNT
    }

    def "test authentication when domain is linked and first time user can authenticate in ldap"() {
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        AccountManager accountManager = Mock(AccountManager)

        def ldapAuthenticator = new LdapAuthenticator()
        ldapAuthenticator._ldapManager = ldapManager
        ldapAuthenticator._userAccountDao = userAccountDao
        ldapAuthenticator._accountManager = accountManager

        long domainId = 1;
        String username = "rajanik"
        LdapManager.LinkType type = LdapManager.LinkType.GROUP
        String name = "CN=test,DC=ccp,DC=citrix,DC=com"

        ldapManager.isLdapEnabled() >> true
        userAccountDao.getUserAccount(username, domainId) >> null
        ldapManager.getDomainLinkedToLdap(domainId) >> new LdapTrustMapVO(domainId, type, name, (short) 0)
        ldapManager.getUser(username, type.toString(), name) >> new LdapUser(username, "email", "firstname", "lastname", "principal", "domain", false)
        ldapManager.canAuthenticate(_, _) >> true
        //user should be created in cloudstack
        accountManager.createUserAccount(username, "", "firstname", "lastname", "email", null, username, (short) 2, domainId, username, null, _, _, User.Source.LDAP) >> Mock(UserAccount)

        when:
        Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> result = ldapAuthenticator.authenticate(username, "password", domainId, null)
        then:
        result.first() == true
        result.second() == null
    }

    def "test authentication when domain is linked and existing user can authenticate in ldap"() {
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        AccountManager accountManager = Mock(AccountManager)

        def ldapAuthenticator = new LdapAuthenticator()
        ldapAuthenticator._ldapManager = ldapManager
        ldapAuthenticator._userAccountDao = userAccountDao
        ldapAuthenticator._accountManager = accountManager

        long domainId = 1;
        String username = "rajanik"
        LdapManager.LinkType type = LdapManager.LinkType.GROUP
        String name = "CN=test,DC=ccp,DC=citrix,DC=com"

        ldapManager.isLdapEnabled() >> true
        UserAccount userAccount = Mock(UserAccount)
        userAccountDao.getUserAccount(username, domainId) >> userAccount
        userAccount.getId() >> 1
        userAccount.getState() >> Account.State.disabled.toString()
        ldapManager.getDomainLinkedToLdap(domainId) >> new LdapTrustMapVO(domainId, type, name, (short) 2)
        ldapManager.getUser(username, type.toString(), name) >> new LdapUser(username, "email", "firstname", "lastname", "principal", "domain", false)
        ldapManager.canAuthenticate(_, _) >> true
        //user should be enabled in cloudstack if disabled
        accountManager.enableUser(1) >> userAccount

        when:
        Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> result = ldapAuthenticator.authenticate(username, "password", domainId, null)
        then:
        result.first() == true
        result.second() == null
    }

    def "test authentication when domain is linked and user cannot authenticate in ldap"() {
        LdapManager ldapManager = Mock(LdapManager)
        UserAccountDao userAccountDao = Mock(UserAccountDao)
        AccountManager accountManager = Mock(AccountManager)

        def ldapAuthenticator = new LdapAuthenticator()
        ldapAuthenticator._ldapManager = ldapManager
        ldapAuthenticator._userAccountDao = userAccountDao
        ldapAuthenticator._accountManager = accountManager

        long domainId = 1;
        String username = "rajanik"
        LdapManager.LinkType type = LdapManager.LinkType.GROUP
        String name = "CN=test,DC=ccp,DC=citrix,DC=com"

        ldapManager.isLdapEnabled() >> true
        UserAccount userAccount = Mock(UserAccount)
        userAccountDao.getUserAccount(username, domainId) >> userAccount
        ldapManager.getDomainLinkedToLdap(domainId) >> new LdapTrustMapVO(domainId, type, name, (short) 2)
        ldapManager.getUser(username, type.toString(), name) >> new LdapUser(username, "email", "firstname", "lastname", "principal", "domain", false)
        ldapManager.canAuthenticate(_, _) >> false

        when:
        Pair<Boolean, UserAuthenticator.ActionOnFailedAuthentication> result = ldapAuthenticator.authenticate(username, "password", domainId, null)
        then:
        result.first() == false
        result.second() == UserAuthenticator.ActionOnFailedAuthentication.INCREMENT_INCORRECT_LOGIN_ATTEMPT_COUNT
    }
}
