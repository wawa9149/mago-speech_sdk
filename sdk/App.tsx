import React, {useState} from 'react';
import AudioRecord from 'react-native-audio-record';
import {NativeModules} from 'react-native';
import {View, Text, TouchableOpacity} from 'react-native';
import {Buffer} from 'buffer';
import RNFS from 'react-native-fs';

const {SlieroVadDetector} = NativeModules;

const App: React.FC = () => {
  const [isRecording, setIsRecording] = useState(false);
  const [audioChunks, setAudioChunks] = useState<Buffer[]>([]);
  const [audioFile, setAudioFile] = useState('');
  let isStart = false;
  const modelPath = 'silero_vad.onnx';

  const startRecording = () => {
    try {
      setIsRecording(true);
      setAudioFile('');
      setAudioChunks([]);

      SlieroVadDetector.initialize(
        modelPath,
        0.6,
        0.45,
        16000,
        500,
        500,
        (error: any, message: any) => {
          if (error) {
            console.error('Initialization error:', error);
          } else {
            console.log('Initialization message:', message);
          }
        },
      );

      // Start streaming audio
      const options = {
        sampleRate: 16000,
        channels: 1,
        bitsPerSample: 16,
        audioSource: 6,
        wavFile: 'audio.wav',
      };

      AudioRecord.init(options);

      // start가 떴을때 7초, start가 없을때 7초 후 timeout
      let timeoutId1: NodeJS.Timeout | undefined;
      let timeoutId2: NodeJS.Timeout | undefined;

      AudioRecord.on('data', async data => {
        try {
          const chunk = Buffer.from(data, 'base64');
          const int16Array = new Int16Array(chunk.buffer);
          const audioData = Array.from(int16Array);

          setAudioChunks(prevChunks => [...prevChunks, chunk]);

          if (isStart && !timeoutId2) {
            if (!timeoutId2) {
              timeoutId2 = setTimeout(() => {
                timeoutId2 = undefined; // timeoutId 초기화
                stopRecording('save');
              }, 7000);
            }
          }

          if (!isStart && !timeoutId1) {
            timeoutId1 = setTimeout(() => {
              timeoutId1 = undefined; // timeoutId 초기화
              stopRecording('');
            }, 7000);
          }
          await SlieroVadDetector.apply(
            audioData,
            true, // 또는 필요한 옵션으로 설정
            (successMessage: any, result: any) => {
              console.log('Success:', result);
              // console.log('audioData: ', audioData.length);
              if (result === 'end') {
                clearTimeout(timeoutId2);
                //listener.remove();
                timeoutId2 = undefined;
                stopRecording('save');
              } else if (result === 'start') {
                isStart = true;
                clearTimeout(timeoutId1);
                timeoutId1 = undefined;
              }
            },
            (errorMessage: any) => {
              console.error('Error:', errorMessage);
            },
          );
        } catch (error) {
          console.error('Error processing audio data:', error);
        }
      });
      AudioRecord.start();
    } catch (error) {
      console.error('Error starting recording:', error);
    }
  };

  const stopRecording = async (saveToFile: String) => {
    await AudioRecord.stop();
    setIsRecording(false);
    isStart = false;
    SlieroVadDetector.close();

    if (saveToFile === 'save') {
      const fullBuffer = Buffer.concat(audioChunks);
      saveAudioToFile(fullBuffer);
      console.log(fullBuffer.length);
    }

    console.log('stopRecording');
  };

  const saveAudioToFile = async (audioBuffer: Buffer) => {
    const filePath = RNFS.ExternalDirectoryPath + '/recordedAudio.wav';

    try {
      const wavHeader = createWavHeader(audioBuffer.length);
      const wavBuffer = Buffer.concat([wavHeader, audioBuffer]);
      // const wavBuffer = audioBuffer;

      await RNFS.writeFile(filePath, wavBuffer.toString('base64'), 'base64');
      console.log('Audio file saved:', filePath);
    } catch (error) {
      console.error('Failed to save audio file', error);
    }
  };

  const createWavHeader = (dataLength: number) => {
    const totalDataLength = dataLength + 44;

    const buffer = Buffer.alloc(44);
    buffer.write('RIFF', 0);
    buffer.writeUInt32LE(totalDataLength, 4);
    buffer.write('WAVEfmt ', 8);
    buffer.writeUInt32LE(16, 16);
    buffer.writeUInt16LE(1, 20); // AudioFormat (1 for PCM)
    buffer.writeUInt16LE(1, 22); // NumChannels (1 for mono)
    buffer.writeUInt32LE(16000, 24); // SampleRate
    buffer.writeUInt32LE(16000 * 2, 28); // ByteRate
    buffer.writeUInt16LE(2, 32); // BlockAlign
    buffer.writeUInt16LE(16, 34); // BitsPerSample
    buffer.write('data', 36);
    buffer.writeUInt32LE(dataLength, 40);

    return buffer;
  };

  return (
    <View style={{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
      <Text>{isRecording ? 'Recording...' : 'Not Recording'}</Text>
      <TouchableOpacity
        onPress={() =>
          isRecording ? stopRecording('save') : startRecording()
        }>
        <Text>{isRecording ? 'Stop Recording' : 'Start Recording'}</Text>
      </TouchableOpacity>
    </View>
  );
};

export default App;
