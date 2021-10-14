# Kagami

Personal slideshow application.

## File format (Example)

```
(30;16:9)

[1]
Color=0;0;0;255

{TEXT}

LINE=Hello, world!
LINE=This is just some text,
LINE=which has multiple lines.
X=0.5
Y=0.5
Size=0.5
Color=255;255;255;255
Underline=FALSE
Strikethrough=FALSE
Reversed=FALSE

{RECT}

X=0.5
Y=0.5
W=0.5
H=0.5
Color=255;255;0;10;
BorderSize=0.01
BorderColor=255;0;0;255
Rotation=0

{OVAL}

X=0.5
Y=0.5
W=0.5
H=0.5
Color=255;255;0;10;
BorderSize=0.01
BorderColor=255;0;0;255
Rotation=0

{IMAGE}

FILE=C:/Users/Foo/mySlideshow.kagami
X=0.5
Y=0.5
W=0.5
H=0.5
Color=255;255;0;10;
BorderSize=0.01
BorderColor=255;0;0;255
Rotation=42

[Another Slide]
Color=0;0;0;255

{TEXT}

LINE=Slide 2

{TEXT}

LINE=Slide
y=0.8
```

## How to build

You need to have OpenJDK 17 installed on your system.

### Universal

```
java.exe ./build.java --build
```

