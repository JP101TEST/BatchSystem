# หัวข้อ

- [วิธีการติดตั้ง](#วิธีการติดตั้ง)
- [ทดสอบระบบค้นหาและโหลด](#ทดสอบระบบค้นหาและโหลด)
- [ไฟล์สำหรับทดสอบ](#ไฟล์สำหรับทดสอบ)

## <p>### อัปเดต ###</p>

#### การอัปโหลด

- เพิ่มการตั้งชื่อไฟล์ใหม่เมื่อ file มีชื่อซ้ำ
- การตรวจเช็คชื่อไฟล์
- การเลือก entity type

#### การ Batch

- เพิ่มการดึง file จาก backup กรณีที่ file ใน local หาย
- เพิ่มการจัดการไฟล์ที่ไมาสามารถแปลงข้อมูลได้

#### search

- เพิ่มการทำ pagination
- วิธีการ search

``` 
{
  "username" : "",
  "gender" : "",
  "dediae.mvp" : null ,
  "dediae.mvps" : null ,
  "dediae.normale": null,
  "page" : 1 
} 
// "page" : 1 จำเป็นไม่สามารถลบทิ้งได้
// key ส่วนอื่นสามารถเพิ่มขึ้นได้ตามใจชอบ
```

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
แนะนำ!!
เพราะการโหลดข้อมูล

สามารถเปิดโดใช้ผ่าน Go Live หรือได้ด้วยวิธีการอื่นๆที่ต้องมีการจำลองเซิฟเวอร์เพื่อให้สามารถเรียกใช้ api ได้

| api                 | คำอธิบาย                                                 | ประภท input |
|---------------------|----------------------------------------------------------|-------------|
| `/upload`           | ใช้อัปโหลดไฟล์ csv json txt xlsx xml                     | from-data  
| `/getAllOrderError` | ค้นหา order การอ่านแปลงไฟล์เข้าฐานข้อมูล                 |             |
| `/search`           | ค้นหาด้วย input json และต้องมี page เพื่อระบุหน้า         | json        |
| `/download`         | ค้นหาด้วยการ search แล้วโหลด ไม่สามารถใช้กับ postman ได้ | json        |

/upload

| key | type |คำอธิบาย|
|-|-|-|
|file |file| ไฟล์ที่ต้องการ upload|
|database_type|text| sql หรือ nosql |
|entity_type|text| ใช้กรณี database_type เป็น sql ให้เลือกข้อมูลที่ต้องการแปลง <br> ในตอนนี้มีแค่ person|

/search , /download
``` json
// page ที่ห้ามหายใน /search เพราะใช้ทำ pagination
{
    "username" : "sd",
    "gender" : "",
    "dediae.mvp" : null ,
    "dediae.mvps" : null ,
    "dediae.normale": null,
    "page" : 1
}
```

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

ไฟล์ MOCK.csv จะไฟล์ที่ใช้สำหรับทดลองแปลงไฟล์ csv หรือ xlsx สำหรับการแปลง column ให้เป็น object json
แต่ก่อนใช้ควรเข้าไปแก้ไขโค้ดใน

[src\main\java\com\batch\service\Batching.java](src/main/java/com/batch/service/Batching.java)

ในบรรทัด 116 ถึง 130 ให้ทำการปลี่ยนเป็น commented

``` java
// ### sql ###
// เลือกประเภท entity ที่ต้องการแปลง
switch (orderList.getString("entityType")) {
    case "person":
    // แปลงเป็น object class entity
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
    break;
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
