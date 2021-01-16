#!/bin/bash

if [ $1 = "start" ] ; then
  JAR_NAME=`ls -vr | grep "crypto-bot-.*\.jar" | head -n 1`
  echo "開始します $JAR_NAME"
  nohup java -jar $JAR_NAME > /dev/null 2>&1 &
elif [ $1 = "stop" ] ; then
  echo "終了します"
  PID=`ps --no-heading -C java -o pid | tr -d " "`
  kill -15 $PID
elif [ $1 = "status" ] ; then
  PID=`ps --no-heading -C java -o pid | tr -d " "`
  if [ -n "$PID" ] ; then
    echo "起動中です $PID"
  else
    echo "停止中です"
  fi
else
  echo "start/stop/statusのいずれかを指定してください"
fi
