spring.application.name=HotelBooking
server.port=9090

# MySQL Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/hotelBookingProject
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD

spring.jpa.hibernate.ddl-auto=update

# JWT Secret
secreteJwtString=YOUR_JWT_SECRET

# Mail configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=YOUR_EMAIL
spring.mail.password=YOUR_APP_PASSWORD

# Redis configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Stripe Keys
stripe.api.public.key=YOUR_STRIPE_PUBLIC_KEY
stripe.api.secret.key=YOUR_STRIPE_SECRET_KEY
