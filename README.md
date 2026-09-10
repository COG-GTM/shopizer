# Shopizer 3 (Java 21, Spring Boot 3.5.16)

3.2.7



[![last_version](https://img.shields.io/badge/last_version-v3.2.7-blue.svg?style=flat)](https://github.com/shopizer-ecommerce/shopizer/tree/3.2.7)
[![Official site](https://img.shields.io/website-up-down-green-red/https/shields.io.svg?label=official%20site)](http://www.shopizer.com/)
[![Docker Pulls](https://img.shields.io/docker/pulls/shopizerecomm/shopizer.svg)](https://hub.docker.com/r/shopizerecomm/shopizer)
[![stackoverflow](https://img.shields.io/badge/shopizer-stackoverflow-orange.svg?style=flat)](http://stackoverflow.com/questions/tagged/shopizer)
[![CircleCI](https://circleci.com/gh/shopizer-ecommerce/shopizer.svg?style=svg)](https://circleci.com/gh/shopizer-ecommerce/shopizer)


Java open source e-commerce software

Headless commerce and Rest api for ecommerce

- Catalog
- Shopping cart
- Checkout
- Merchant
- Order
- Customer
- User

Shopizer Headless commerce consists of the following components:


Access the headless api: http://localhost:8080/swagger-ui/index.html

## Java 11 → 21 migration (Spring Boot 3.5.16)

- Java 11 → 21
- Spring Boot 2.5.12 → 3.5.16 (Spring Framework 6.2, Spring Security 6.5, Hibernate 6.6, Jakarta EE 10)
- Springfox 2.9.2 → springdoc-openapi 2.8.x (`/swagger-ui/index.html`, `/v3/api-docs`)
- Ehcache 2 → Ehcache 3 through JCache
- Caffeine remains at 2.9.3 because Infinispan 9.4 requires the Caffeine 2.x `CacheWriter` API
- Drools 7.32 → 8.44 (`kie-ci` dropped)
- Infinispan 9.4.18 → 9.4.24
- jjwt 0.8 → 0.9.1
- MapStruct 1.3 → 1.6.3
- H2 1.4 → 2.x (`mv_store=false` removed and the shipped database regenerated)
- MySQL dialect renamed for Hibernate 6
- Java EE `javax` packages migrated to Jakarta EE `jakarta`
- `WebSecurityConfigurerAdapter` replaced with `SecurityFilterChain` beans
- External starters wired through `AutoConfiguration.imports`


See the demo: [**New demo on the way 2025]
-------------------
Headless demo Available soon

1.  Run from Docker images:

From the command line:

```
docker run -p 8080:8080 shopizerecomm/shopizer:latest
```
       
2. Run the administration tool

⋅⋅⋅ Requires the java backend to be running

```
docker run \
 -e "APP_BASE_URL=http://localhost:8080/api" \
 -p 82:80 shopizerecomm/shopizer-admin
```


3. Run react shop sample site

⋅⋅⋅ Requires the java backend to be running

```
docker run \
 -e "APP_MERCHANT=DEFAULT"
 -e "APP_BASE_URL=http://localhost:8080"
 -p 80:80 shopizerecomm/shopizer-shop-reactjs
```

API documentation:
-------------------


Get the source code:
-------------------
Clone the repository:
     
	 $ git clone git://github.com/shopizer-ecommerce/shopizer.git
	 

To build the application:
-------------------

1. Shopizer backend


From the command line:

	$ cd shopizer
	$ mvnw clean install
	$ cd sm-shop
	$ mvnw spring-boot:run

2. Shopizer admin

Form compiling and running Shopizer admin consult the repo README file

3. Shop sample site

Form compiling and running Shopizer admin consult the repo README file


### Access the application:
-------------------

Access the headless web application at: http://localhost:8080/swagger-ui.html


The instructions above will let you run the application with default settings and configurations.
Please read the instructions on how to connect to MySQL, configure an email server and configure other subsystems


### Documentation:
-------------------

Documentation available [<https://shopizer-ecommerce.github.io/documentation/>](http://localhost:8080/swagger-ui/index.html)

ChatOps <https://shopizer.slack.com>  - Join our Slack channel <https://communityinviter.com/apps/shopizer/shopizer>

More information is available on shopizer web site here <http://www.shopizer.com>

### Participation:
-------------------

If you have interest in giving feedback or for participating to Shopizer project in any way
Feel to use the contact form <http://www.shopizer.com/contact.html> and share your email address
so we can send an invite to our Slack channel

### How to Contribute:
-------------------
Fork the repository to your GitHub account

Clone from fork repository
-------------------

       $ git clone https://github.com/yourusername/shopizer.git

Build application according to steps provided above


Create new branch in your repository
-------------------

	   $ git checkout -b branch-name


Push your changes to Shopizer
-------------------

Please open a PR (pull request) in order to have your changes merged to the upstream
