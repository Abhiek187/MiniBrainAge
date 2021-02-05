# Mini Brain Age

<img src="brainage.png" alt="Mini Brain Age" width="250px">

Inspired by Brain Age, this Android game tests players' math knowledge by hand drawing numbers and quickly solving math problems. A key feature of this app is that it uses on-device machine learning to interpret the handwritten digits. This is done by feeding the MNIST dataset into a neural network created in TensorFlow Lite.

## How to Run

To run the app, [clone](https://github.com/Abhiek187/MiniBrainAge.git) this repo and run it in Android Studio.

There are 3 Python files that can be executed after installing the [TensorFlow module](https://www.tensorflow.org/install/pip):

- `train_ml_model.py` creates the initial MNIST neural network with 98% accuracy
- `improve_accuracy.py` utilizes data augmentation to improve the accuracy on mobile devices
- `write_metadata.py` generates info about the tflite file that can be viewed on code generators, such as Android Studio's ML Binding

`write_metadata.py` is executed with the following syntax:
```
python3 ./metadata_writer_for_image_classifier.py \
    --model_file=./<path-to>/mnist.tflite \
    --label_file=./labels.txt \
    --export_directory=<output-directory>
```
