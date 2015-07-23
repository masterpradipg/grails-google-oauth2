package grails.plugin.googleOAuth2

import grails.test.GrailsMock
import grails.test.mixin.Mock
import grails.test.mixin.TestFor

@TestFor(GoogleOAuth2Controller)
@Mock([GoogleOAuth2Filters,GoogleOAuth2Service])
class GoogleOAuth2FiltersTests {

	def googleOAuth2ServiceControl

	void mockConfig(Boolean storeCredentialInSession) {
		controller.grailsApplication.config.googleOAuth2.currentUserRef = {->"test@test.com"}
		controller.grailsApplication.config.googleOAuth2.interceptUrlList = ['/admin/action1',
		                                                                     '/admin/action2']
		controller.grailsApplication.config.googleOAuth2.storeCredentialInSession = storeCredentialInSession
	}

	void mockServices() {
		// Due to the strange way in testing filters, this convoluted
		// method of mocking is required for services used in the filter
		// See simpler example here: http://www.intelligrape.com/blog/2014/08/04/grails-unit-test-filters-with-the-injected-service/
		defineBeans {
			googleOAuth2ServiceControl(GrailsMock, GoogleOAuth2Service)
			googleOAuth2Service(googleOAuth2ServiceControl:"createMock")
		}
		googleOAuth2ServiceControl = applicationContext.getBean("googleOAuth2ServiceControl")
	}

	void testUnfilteredAction() {
		mockConfig(true)
		request.forwardURI = '/admin/unfiltered'

		controller.metaClass.unfiltered {-> render "OK"}

		withFilters(action:'unfiltered') {
			controller.unfiltered()
		}
		assert null == response.redirectedUrl
		assert 200 == response.status
	}

	void testCredentialInSession() {
		mockConfig(true)

		request.forwardURI = '/admin/action1'
		session.googleCredential = new Object()

		controller.metaClass.filtered {-> render "OK"}

		withFilters(action:'action1') {
			controller.filtered()
		}
		assert null == response.redirectedUrl
		assert 200 == response.status
	}

	void doTestActionFilteredWhenCredentialNotInDB(String action) {
		mockConfig(true)
		mockServices()

		request.addHeader('referer','/refering/page')
		request.forwardURI = "/admin/$action"

		googleOAuth2ServiceControl.demand.loadCredential { String ref -> null }

		withFilters(action: action) {
			controller.edit()
		}
		assert "/googleOAuth2/authorize" == response.redirectedUrl
		assert "/admin/$action" == session.googleAuthSuccess
		assert "/refering/page" == session.googleAuthFailure
		assert 302 == response.status
	}

	void testActionFilteredAction1() {
		doTestActionFilteredWhenCredentialNotInDB("action1")
	}

	void testActionFilteredAction2() {
		doTestActionFilteredWhenCredentialNotInDB("action2")
	}

	void testCredentialNotPlacedInSessionWhenInDB() {
		mockConfig(false)
		mockServices()

		request.forwardURI = '/admin/action1'
		def dummyCredential = new Object()

		googleOAuth2ServiceControl.demand.loadCredential { String ref ->
			assert "test@test.com" == ref
			dummyCredential }

		controller.metaClass.edit {-> render "OK"}

		withFilters(action:'action1') {
			controller.edit()
		}
		assert null == response.redirectedUrl
		assert 200 == response.status
		assert null == session.googleCredential
	}

	void testCredentialPlacedInSessionWhenInDB() {
		mockConfig(true)
		mockServices()

		request.forwardURI = '/admin/action1'
		def dummyCredential = new Object()

		googleOAuth2ServiceControl.demand.loadCredential { String ref ->
			assert "test@test.com" == ref
			dummyCredential }

		controller.metaClass.edit {-> render "OK"}

		withFilters(action:'action1') {
			controller.edit()
		}
		assert null == response.redirectedUrl
		assert 200 == response.status
		assert dummyCredential == session.googleCredential
	}
}
