# หัวข้อ
- [วิธีการติดตั้ง](#วิธีการติดตั้ง)
- [ทดสอบระบบค้นหาและโหลด](#ทดสอบระบบค้นหาและโหลด)
- [ไฟล์สำหรับทดสอบ](#ไฟล์สำหรับทดสอบ)
## <p>### อัปเดต ###</p>
#### การอัปโหลด
- เพิ่มการตั้งชื่อไฟล์ใหม่เมื่อ file มีชื่อซ้ำ
- การตรวจเช็คชื่อไฟล์
#### การ Batch 
-เพิ่มการดึง file จาก backup กรณีที่ file ใน local หาย
- #### คำเตือน
    - ถ้าไฟล์เป็น nosql แต่ตั้งค่า database type เป็น sql ยังไม่ได้แก้ปัญหา
## <a name="วิธีการติดตั้ง"></a>วิธีการติดตั้ง
ทำการสร้าง docker ที่ [docker-compose.yml](docker-compose.yml) เพื่อเตรียม database 
```
services:
  db:
    image: mysql:8.0
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: datasql
      MYSQL_PASSWORD: root
    ports:
      - "3306:3306"

  phpmyadmin:
    image: phpmyadmin/phpmyadmin
    restart: always
    ports:
      - "8082:80"
    environment:
      PMA_HOST: db
      PMA_PORT: 3306
    depends_on:
      - db

  mongodb:
    image: mongo
    restart: always
    environment:
      MONGO_INITDB_ROOT_USERNAME: root 
      MONGO_INITDB_ROOT_PASSWORD: root
    ports:
      - "27017:27017"
```
การเข้าถึง mongodb ต้องเข้าถึงด้วย URI
```
mongodb://root:root@localhost:27017/ 
```
- root ตำแหน่งแรก คือ USERNAME
- root ตำแหน่งสอง คือ PASSWORD

หากต้องการใช้ tool เพื่อให้ง่ายในการจัดการฐานข้อมูลแบบ phpMyAdmin ก็สามารถใช้ MongoDBCompass 

## <a name="ทดสอบระบบค้นหาและโหลด"></a>ทดสอบระบบค้นหาและโหลด
มีการสร้าง Webpage สำหรับการทดสอบการค้นหาและโหลดข้อมูลที่ค้นเป็นไฟล์ json ได้ที่
[webpage](upload.html)

สามารถเปิดโดใช้ผ่าน Go Live หรือได้ด้วยวิธีการอื่นๆที่ต้องมีการจำลองเซิฟเวอร์เพื่อให้สามารถเรียกใช้ api ได้
## <a name="ไฟล์สำหรับทดสอบ"></a>ไฟล์สำหรับทดสอบ
ในโฟลเดอร์ dataset มีการเตรียมไฟล์ที่ใช้ในการอัปโหลด โดยการอัปโหลดจะต้องทำใน
```
MOCK-DATA.csv
MOCK-DATA.json
MOCK-DATA.txt
MOCK-DATA.xlsx
MOCK-DATA.xml
MOCK.csv
```
ไฟล์ MOCK.csv จะไฟล์ที่ใช้สำหรับทดลองแปลงไฟล์ csv หรือ xlsx สำหรับการแปลง column ให้เป็น object json แต่ก่อนใช้ควรเข้าไปแก้ไขโค้ดใน 

[src\main\java\com\batch\service\Batching.java](src\main\java\com\batch\service\Batching.java)

ในบรรทัด 112 ถึง 130 ให้ทำการปลี่ยนเป็น commented
``` java
boolean useSql = true;
if (useSql == USER_SQL) {
    // ### sql ###
    PersonSql personSql = objectMapper.readValue(data.get((int) startReadLine).toString(), PersonSql.class);
    //System.out.println("personSql : " + personSql);
    Optional<PersonSql> optionalPersonSql = personSqlRepository.findByUsername(personSql.getUsername());
    if (optionalPersonSql.isPresent()) {
        PersonSql existingPersonSql = optionalPersonSql.get();
        // Update the existing entity with the new values from personSql
        existingPersonSql.setFirst_name(personSql.getFirst_name());
        existingPersonSql.setLast_name(personSql.getLast_name());
        existingPersonSql.setGender(personSql.getGender());
        // Set other fields as necessary
        personSqlRepository.save(existingPersonSql);
    } else {
        // If no existing entity, save the new one
        personSqlRepository.save(personSql);
    }
}
```
หรือเปลี่ยนตำแปร useSql เป็น false
 

ตัวอย่างการแปลงไฟล์ MOCK.csv

ไฟล์ csv 
``` 
username,name.first_name,name.last_name,gender
user_a, first_name_a, last_name_a
user_b, first_name_b, last_name_b
```
แปลงเป็น json
``` json
[
  {
    "username" : "user_a",
    "name" : {
      "first_name" : "first_name_a",
      "last_name" : "last_name_a"
    }
  },
  {
    "username" : "user_b",
    "name" : {
      "first_name" : "first_name_b",
      "last_name" : "last_name_b"
    }
  }
]

```