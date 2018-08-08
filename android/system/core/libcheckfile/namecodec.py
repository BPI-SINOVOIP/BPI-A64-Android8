import os
import sys
import string

mask = 0x75
ti=""

def decode(code,key):
    global ti
    num = 0
    for i in range(0,len(code)):
        if num<len(code)-1:
            c='0x'+code[num]+code[num+1]
            c= string.atoi(c,16)
            c=c^key
            c=chr(c)
            ti=ti+c
            num = num + 2
    print ti

def encode(name,key):
    global ti
    for i in name:
        c=ord(i)
        c=c^key
        ti=ti+'%02x'%c
    print ti

if __name__=='__main__':
    if len(sys.argv)==3:
        if(sys.argv[1] == 'encode'):
            encode(sys.argv[2],mask)
        elif(sys.argv[1] == 'decode'):
            decode(sys.argv[2],mask)
    else:
        print 'usage: namecodec encode/decode xxxxxxxxxxxx'
